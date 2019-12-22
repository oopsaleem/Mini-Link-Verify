package com.kgwb;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.kgwb.model.MiniLinkDeviceTmprWrapper;
import com.kgwb.model.SlotTmprWrapper;
import javafx.concurrent.Task;
import net.sf.expectit.Expect;
import net.sf.expectit.ExpectBuilder;
import net.sf.expectit.Result;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.sf.expectit.filter.Filters.removeColors;
import static net.sf.expectit.filter.Filters.removeNonPrintable;
import static net.sf.expectit.matcher.Matchers.regexp;
import static net.sf.expectit.matcher.Matchers.sequence;

public class LongRunningTask extends Task<List<MiniLinkDeviceTmprWrapper>> {

    private final String filePath;
    private static final String CONS_PROMPT_PATTERN = ">";
    private static final String CONS_USER = "foo";
    private static final String CONS_PWD = "bar";
    private static final int CONS_PORT = 22;
    private static final String CONS_CMD = "show mac-address-table";

    public LongRunningTask(String filePath) {
        this.filePath = filePath;
    }

    @Override
    protected List<MiniLinkDeviceTmprWrapper> call() {
        ExecutorService executor = Executors.newCachedThreadPool();

        FileInputStream file;
        int itemCount = 0;
        try {
            file = new FileInputStream(new File(filePath));
            List<Callable<Map.Entry<String, String>>> listOfCallable = new ArrayList<>();
            try {
                XSSFWorkbook workbook = new XSSFWorkbook(file);
                XSSFSheet sheet = workbook.getSheetAt(0);
                Iterator<Row> rowIterator = sheet.iterator();

                rowIterator.next();
                while (rowIterator.hasNext()) {
                    Row row = rowIterator.next();
                    final String NE_Name = row.getCell(1).getStringCellValue();
                    final String IPv4 = row.getCell(2).getStringCellValue();

                    if (IPv4 == null || IPv4.trim().isEmpty()) continue;

                    itemCount++;

                    listOfCallable.add(() -> {
                        try {
                            JSch jSch = new JSch();
                            Session session = jSch.getSession(CONS_USER, IPv4, CONS_PORT);
//                            session.setPassword(CONS_PWD);
                            Properties properties = new Properties();
                            properties.put("PreferredAuthentications", "publickey,keyboard-interactive,password");
                            properties.put("StrictHostKeyChecking", "no");
                            properties.put("UseDNS", "no");
                            session.setConfig(properties);
                            session.connect();
                            session.setTimeout(15 * 1_000);
                            Channel channel = session.openChannel("shell");
                            channel.connect();

                            try (Expect expect = new ExpectBuilder()
                                    .withTimeout(10 , TimeUnit.SECONDS)
                                    .withOutput(channel.getOutputStream())
                                    .withInputs(channel.getInputStream(), channel.getExtInputStream())
                                    .withEchoInput(System.out)
                                    .withEchoOutput(System.err)
                                    .withInputFilters(removeColors(), removeNonPrintable())
                                    .withExceptionOnFailure()
                                    .build()) {
                                expect.expect(regexp("assword: "));
                                for (char c : CONS_PWD.toCharArray()) expect.send(String.valueOf(c));
                                expect.sendLine("");
                                expect.expect(regexp(CONS_PROMPT_PATTERN));
                                expect.sendLine(CONS_CMD);
                                Result commandResult = expect.expect(sequence(regexp(".+"), regexp(CONS_PROMPT_PATTERN)));

                                return new SimpleEntry<>(NE_Name, commandResult.getInput());
                            } finally {
                                channel.disconnect();
                                session.disconnect();
                            }
                        } catch (Exception e) {
                            return new SimpleEntry<>(NE_Name, "EXCEPTION:" + e.getMessage());
                        }
                    });
                }
            } catch (IOException e) {
                updateMessage(e.getMessage());
            } finally {
                try {
                    file.close();
                } catch (IOException e) {
                    updateMessage(e.getMessage());
                }
            }

            final int max = itemCount;

            List<Future<Map.Entry<String, String>>> futures = executor.invokeAll(listOfCallable);
            List<MiniLinkDeviceTmprWrapper> listOfmlDevWrapper = new ArrayList<>();
            AtomicInteger iterations = new AtomicInteger();
            futures.stream().map(f -> {
                try {
                    return f.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return e.getMessage();
                }
            }).forEach(cmdResult -> {
                iterations.getAndIncrement();
                SimpleEntry deviceRAW = (SimpleEntry) cmdResult;
                updateProgress(iterations.get() + 1, max);

                String comment = null;

                try {
                    String rawData = (String) deviceRAW.getValue();
                    if(rawData.startsWith("EXCEPTION:"))
                        comment = rawData;
                    else if (rawData.contains("VLAN MAC Address"))
                        comment = "MAC Available";
                    else
                        comment = "NO MAC";//rawData;
                } catch (Exception e) {
                    comment = e.getMessage();
                }

                listOfmlDevWrapper.add(
                        new MiniLinkDeviceTmprWrapper((String) deviceRAW.getKey(), "", comment == null ? "" : comment));
            });

            return listOfmlDevWrapper;
        } catch (FileNotFoundException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }

        return null;
    }

    private List<SlotTmprWrapper> process(String showTempratureRaw) throws Exception {
        List<SlotTmprWrapper> slotTmprWrappers = new ArrayList<>();

        Pattern regex = Pattern.compile("^\\s+(?<slot>\\d+)\\s+(?<temp>\\d+)\\s+(?<high>\\d+)\\s+(?<exce>\\d+)", Pattern.MULTILINE);
        Matcher matcher = regex.matcher(showTempratureRaw);
        while(matcher.find()) {
            int slot = Integer.parseInt(matcher.group("slot"));
            int temp = Integer.parseInt(matcher.group("temp"));
            int high = Integer.parseInt(matcher.group("high"));
            int exce = Integer.parseInt(matcher.group("exce"));

            slotTmprWrappers.add(new SlotTmprWrapper(slot, temp, high, exce));
        }
        return slotTmprWrappers;
    }
}
