package com.kgwb;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.kgwb.model.MiniLinkDeviceVerifyWrapper;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.sf.expectit.filter.Filters.removeColors;
import static net.sf.expectit.filter.Filters.removeNonPrintable;
import static net.sf.expectit.matcher.Matchers.regexp;
import static net.sf.expectit.matcher.Matchers.sequence;

public class LongRunningTask extends Task<List<MiniLinkDeviceVerifyWrapper>> {

    private final String filePath;
    private static final String CONS_PROMPT_PATTERN = ">";
    private static final String CONS_USER = "foo";
    private static final String CONS_PWD = "bar";
    private static final int CONS_PORT = 22;

    public LongRunningTask(String filePath) {
        this.filePath = filePath;
    }

    @Override
    protected List<MiniLinkDeviceVerifyWrapper> call() {
        ExecutorService executor = Executors.newCachedThreadPool();

        List<String> commands = new ArrayList<>();
        commands.add("show bridge-basics");
        commands.add("show scheduler-profile 10");
        commands.add("show mac-address-table");
        commands.add("show interface ethernet status");
        commands.add("show bridge-port");

        FileInputStream file;
        int itemCount = 0;
        try {
            file = new FileInputStream(new File(filePath));
            List<Callable<MiniLinkRowData>> listOfCallable = new ArrayList<>();
            try {
                XSSFWorkbook workbook = new XSSFWorkbook(file);
                XSSFSheet sheet = workbook.getSheetAt(0);
                Iterator<Row> rowIterator = sheet.iterator();

                rowIterator.next();
                while (rowIterator.hasNext()) {
                    Row row = rowIterator.next();
                    final String cell_NeName = row.getCell(1).getStringCellValue();
                    final String cell_IPv4 = row.getCell(2).getStringCellValue();

                    if (cell_IPv4 == null || cell_IPv4.trim().isEmpty()) continue;

                    itemCount++;

                    listOfCallable.add(() -> {
                        try {
                            JSch jSch = new JSch();
                            Session session = jSch.getSession(CONS_USER, cell_IPv4, CONS_PORT);
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
                                    .withTimeout(10, TimeUnit.SECONDS)
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
                                Result welcome = expect.expect(sequence(regexp(".+"), regexp(CONS_PROMPT_PATTERN)));
                                //Grab configured node name
                                String neNameConfigured = "";
                                Pattern regexNeName = Pattern.compile("^(?<neName>WR-\\d+-[A-Z0-9]+)>", Pattern.MULTILINE);
                                Matcher matcherNeName = regexNeName.matcher(welcome.getInput());
                                if (matcherNeName.find()) {
                                    neNameConfigured = matcherNeName.group("neName");
                                }
                                //Execute commands
                                Map<String, String> cmds_result = new HashMap<>();
                                for (String cmd : commands) {
                                    expect.sendLine(cmd);
                                    Result commandResult = expect.expect(sequence(regexp(".+"), regexp(CONS_PROMPT_PATTERN)));
                                    cmds_result.put(cmd, commandResult.getInput());
                                }

//                                //Execute commands based on input from previous output
//                                List<String> bridge_port_cmds = new ArrayList<>();
//                                if (cmds_result.containsKey("show interface ethernet status")) {
//                                    String int_eth = cmds_result.get("show interface ethernet status");
//
//                                    Pattern regexBridgePort = Pattern.compile("^LAN (?<lan>[\\d+\\/]+)\\s+", Pattern.MULTILINE);
//                                    Matcher matcherBridgePort = regexBridgePort.matcher(int_eth);
//                                    while (matcherBridgePort.find()) {
//                                        String lan_port = matcherBridgePort.group("lan");
//
//                                        bridge_port_cmds.add(String.format("show bridge-port %s", lan_port));
//                                    }
//
//                                    for (String cmd : bridge_port_cmds) {
//                                        expect.sendLine(cmd);
//                                        Result commandResult = expect.expect(sequence(regexp(".+"), regexp(CONS_PROMPT_PATTERN)));
//                                        cmds_result.put(cmd, commandResult.getInput());
//                                    }
//                                }

                                return new MiniLinkRowData(
                                        neNameConfigured.contentEquals(cell_NeName) ? neNameConfigured : String.format("%s should be %s", cell_NeName, neNameConfigured)
                                        , cell_IPv4
                                        , cmds_result);
                            } finally {
                                channel.disconnect();
                                session.disconnect();
                            }
                        } catch (Exception e) {
                            Map<String, String> cmd_result = new HashMap<>();
                            cmd_result.put("", "EXCEPTION:" + e.getMessage());
                            return new MiniLinkRowData(cell_NeName, cell_IPv4, cmd_result);
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

            List<Future<MiniLinkRowData>> futures = executor.invokeAll(listOfCallable);
            List<MiniLinkDeviceVerifyWrapper> listOfmlDevWrapper = new ArrayList<>();
            AtomicInteger iterations = new AtomicInteger();
            futures.stream().map(f -> {
                try {
                    return f.get(10, TimeUnit.SECONDS);
                } catch (Exception e) {
                    return e.getMessage();
                }
            }).forEach(rawItem -> {
                iterations.getAndIncrement();
                MiniLinkRowData deviceRAW = (MiniLinkRowData) rawItem;
                updateProgress(iterations.get() + 1, max);

                String summaryComment = null;
                String summaryBridgeBasics = null;
                String summarySchedulerProfile = null;
                String summaryInterface_ethernet_status = null;
//                String summaryBridgePort = null;


                try {
                    Map<String, String> rawData = deviceRAW.getRawData(); // Map of command and its result to assess

                    //Assess command printout
                    if (rawData.containsKey("show bridge-basics")) {
                        String rawValue = rawData.get("show bridge-basics");
                        if (rawValue.startsWith("EXCEPTION:"))
                            summaryBridgeBasics = rawValue;
                        else {
                            String assessBridgeBasics = assessBridgeBasics(rawValue);
                            if (!assessBridgeBasics.isEmpty())
                                summaryBridgeBasics = "Missing items: " + assessBridgeBasics;
                        }
                    } else
                        summaryBridgeBasics = "No result for command: show bridge-basics";

                    //Assess command printout
                    if (rawData.containsKey("show scheduler-profile 10")) {
                        String rawValue = rawData.get("show scheduler-profile 10");
                        if (rawValue.startsWith("EXCEPTION:"))
                            summarySchedulerProfile = rawValue;
                        else {
                            String assessSchedulerProfile = assessSchedulerProfile(rawValue);
                            if (!assessSchedulerProfile.isEmpty())
                                summarySchedulerProfile = "Missing items: " + assessSchedulerProfile;
                        }
                    } else
                        summarySchedulerProfile = "No result for command: show scheduler-profile 10";

                    //Assess command printout
                    if (rawData.containsKey("show interface ethernet status") && rawData.containsKey("show bridge-port")) {
                        String statusRawValue = rawData.get("show interface ethernet status");
                        String bridgePortRawValue = rawData.get("show bridge-port");
                        if (statusRawValue.startsWith("EXCEPTION:"))
                            summaryInterface_ethernet_status = statusRawValue;
                        else {
                            String assessInterfaceEthernetStatus = assessInterfaceEthernetStatus(statusRawValue, bridgePortRawValue);
                            if (!assessInterfaceEthernetStatus.isEmpty())
                                summaryInterface_ethernet_status = assessInterfaceEthernetStatus;
                        }
                    } else
                        summaryInterface_ethernet_status = "No result for command: show interface ethernet status && show bridge-port";

//                    //Assess command printout
//                    if (rawData.containsKey("show bridge-port")) {
//                        String rawValue = rawData.get("show bridge-port");
//                        if (rawValue.startsWith("EXCEPTION:"))
//                            summaryBridgePort = rawValue;
//                        else {
//                            String assessBridgePort = assessBridgePort(rawValue);
//                            if (!assessBridgePort.isEmpty())
//                                summaryBridgePort = "Check LAN: " + assessBridgePort;
//                        }
//                    } else
//                        summaryBridgePort = "No result for command: show bridge-port";

                } catch (Exception e) {
                    summaryComment = e.getMessage();
                }

                listOfmlDevWrapper.add(new MiniLinkDeviceVerifyWrapper(
                        deviceRAW.getName(),
                        deviceRAW.getIp(),
                        summaryComment == null ? "" : summaryComment,
                        summaryBridgeBasics == null ? "" : summaryBridgeBasics,
                        summarySchedulerProfile == null ? "" : summarySchedulerProfile,
                        summaryInterface_ethernet_status == null ? "" : summaryInterface_ethernet_status)
                );
            });

            return listOfmlDevWrapper;
        } catch (FileNotFoundException | InterruptedException e) {
            e.printStackTrace();
        } finally {
            executor.shutdown();
        }

        return null;
    }

    private String assessBridgeBasics(String bridgeBasicsPrint) {
        List<String> comments = new ArrayList<>();
        List<String> basics = Arrays.asList(bridgeBasicsPrint.split("\\r\\n|\\r|\\n"));

        if (!basics.contains("bridge variant 4")) comments.add("[bridge variant 4]");
        if (!basics.contains("bridge mode dot1 q")) comments.add("[bridge mode dot1 q]");
        if (!basics.contains("bridge tp-agingtime 300")) comments.add("[bridge tp-agingtime 300]");
        if (!basics.contains("bridge priority-mapping type userdefined")) comments.add("[bridge priority-mapping type userdefined]");
        if (!basics.contains("bridge network-pcp-selection 8p0d")) comments.add("[bridge network-pcp-selection 8p0d]");
        if (!basics.contains("bridge customer-BPDU discard")) comments.add("[bridge customer-BPDU discard]");
        if (!basics.contains("bridge priority-mapping map 0 0")) comments.add("[bridge priority-mapping map 0 0]");
        if (!basics.contains("bridge priority-mapping map 1 1")) comments.add("[bridge priority-mapping map 1 1]");
        if (!basics.contains("bridge priority-mapping map 2 2")) comments.add("[bridge priority-mapping map 2 2]");
        if (!basics.contains("bridge priority-mapping map 3 3")) comments.add("[bridge priority-mapping map 3 3]");
        if (!basics.contains("bridge priority-mapping map 4 4")) comments.add("[bridge priority-mapping map 4 4]");
        if (!basics.contains("bridge priority-mapping map 5 5")) comments.add("[bridge priority-mapping map 5 5]");
        if (!basics.contains("bridge priority-mapping map 6 6")) comments.add("[bridge priority-mapping map 6 6]");
        if (!basics.contains("bridge priority-mapping map 7 7")) comments.add("[bridge priority-mapping map 7 7]");
        if (!basics.contains("bridge l2cpmacdesttunnel a0:ad")) comments.add("[bridge l2cpmacdesttunnel a0:ad]");
        if (!basics.contains("bridge l2cppriority 4")) comments.add("[bridge l2cppriority 4]");
        if (!basics.contains("bridge scheduler-profile 10")) comments.add("[bridge scheduler-profile 10]");
        if (!basics.contains("bridge queue-set-profile 5")) comments.add("[bridge queue-set-profile 5]");
        if (!basics.contains("bridge aging enable")) comments.add("[bridge aging enable]");
        if (!basics.contains("bridge aging 0 60")) comments.add("[bridge aging 0 60]");
        if (!basics.contains("bridge aging 1 60")) comments.add("[bridge aging 1 60]");
        if (!basics.contains("bridge aging 2 60")) comments.add("[bridge aging 2 60]");
        if (!basics.contains("bridge aging 3 60")) comments.add("[bridge aging 3 60]");
        if (!basics.contains("bridge aging 4 60")) comments.add("[bridge aging 4 60]");
        if (!basics.contains("bridge aging 5 24")) comments.add("[bridge aging 5 24]");
        if (!basics.contains("bridge aging 6 24")) comments.add("[bridge aging 6 24]");
        if (!basics.contains("bridge aging 7 24")) comments.add("[bridge aging 7 24]");

        return comments.size() > 0 ? String.join(", ", comments) : "";
    }

    private String assessSchedulerProfile(String schedulerProfilePrint) {
        List<String> comments = new ArrayList<>();
        List<String> schedulerProfile = new ArrayList<>();
        for(String line : schedulerProfilePrint.split("\\r\\n|\\r|\\n")) {
            schedulerProfile.add(line.trim());
        }

        if (!schedulerProfile.contains("name             Mobily_QoS")) comments.add("[name Mobily_QoS]");
//        if (!schedulerProfile.contains("number-of-users  9")) comments.add("[number-of-users  9]");
        if (!schedulerProfile.contains("tc-queue         7      6      5      4      3      2      1      0")) comments.add("[tc-queue         7      6      5      4      3      2      1      0]");
        if (!schedulerProfile.contains("scheduler-type  strict strict strict dwrr   dwrr   dwrr   dwrr   dwrr")) comments.add("[scheduler-type  strict strict strict dwrr   dwrr   dwrr   dwrr   dwrr]");
        if (!schedulerProfile.contains("weight          -      -      -      45     35     10     5      5")) comments.add("[weight          -      -      -      45     35     10     5      5]");

        return comments.size() > 0 ? String.join(", ", comments) : "";
    }

    private String assessInterfaceEthernetStatus(String interfaceEthernetStatusPrint, String bridgePortPrint) {
        List<String> comments = new ArrayList<>();
        Map<String, String> bridgePorts = new HashMap<>();
        Map<String, String> interfaceStatusMap = new HashMap<>();

        Pattern regex = Pattern.compile("^LAN (?<interface>[\\d+\\/]+)\\s+\\d+\\s+(?<role>\\w+)\\s+(?<adminStatus>\\w+)", Pattern.MULTILINE);
        Matcher matcher = regex.matcher(interfaceEthernetStatusPrint);
        while (matcher.find()) {
            String lanInterface = matcher.group("interface");
            String role = matcher.group("role");
            String adminStatus = matcher.group("adminStatus");

            if(adminStatus.toLowerCase().contentEquals("up")) {
                interfaceStatusMap.put(lanInterface, role);
            }
        }

        //Assess bridge Ports
        Pattern regexBridgePort = Pattern.compile("!LAN (?<interface>[\\d+\\/]+)(?<settings>.*?)interface ethernet", Pattern.DOTALL);
        Matcher matcherBridgePort = regexBridgePort.matcher(bridgePortPrint);
        while (matcherBridgePort.find()) {
            String lanInterface = matcherBridgePort.group("interface");
            String settings = matcherBridgePort.group("settings");

            bridgePorts.put(lanInterface, settings);
        }

        Iterator<Map.Entry<String, String>> it = bridgePorts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> pair = it.next();
            it.remove(); // avoids a ConcurrentModificationException

            if ( interfaceStatusMap.containsKey(pair.getKey()) ) {
                List<String> bridgePortSettings = new ArrayList<>();
                for(String line : pair.getValue().split("\\r\\n|\\r|\\n")) {
                    bridgePortSettings.add(line.trim());
                }
                List<String> settingsComment = new ArrayList<>();
                if( !bridgePortSettings.contains("role uni")) settingsComment.add("role uni");
                if( !bridgePortSettings.contains("bridge-port")) settingsComment.add("bridge-port");
                if( !bridgePortSettings.contains("maxfs 2000")) settingsComment.add("maxfs 2000");
                if( !bridgePortSettings.contains("no mac-whitelist")) settingsComment.add("no mac-whitelist");
                if( !bridgePortSettings.contains("admit untagged priority-tagged vlan-tagged")) settingsComment.add("admit untagged priority-tagged vlan-tagged");
                if( !bridgePortSettings.contains("pvid 0")) settingsComment.add("pvid 0");
                if( !bridgePortSettings.contains("no stormctrl bc")) settingsComment.add("no stormctrl bc");
                if( !bridgePortSettings.contains("no stormctrl mc")) settingsComment.add("no stormctrl mc");
                if( !bridgePortSettings.contains("no stormctrl dlf")) settingsComment.add("no stormctrl dlf");
                if( !bridgePortSettings.contains("stormctrl maxbcbw 100")) settingsComment.add("stormctrl maxbcbw 100");
                if( !bridgePortSettings.contains("stormctrl maxmcbw 100")) settingsComment.add("stormctrl maxmcbw 100");
                if( !bridgePortSettings.contains("stormctrl maxdlfbw 100")) settingsComment.add("stormctrl maxdlfbw 100");
                if( !bridgePortSettings.contains("no max-learned-addresses")) settingsComment.add("no max-learned-addresses");
                if( !bridgePortSettings.contains("no forbidden-egressports")) settingsComment.add("no forbidden-egressports");
                if( !bridgePortSettings.contains("l2cp lldp discard")) settingsComment.add("l2cp lldp discard");
                if( !bridgePortSettings.contains("l2cp esmc discard")) settingsComment.add("l2cp esmc discard");
                if( !bridgePortSettings.contains("default-network-priority 0")) settingsComment.add("default-network-priority 0");
                if( !bridgePortSettings.contains("trusted ctagPcp")) settingsComment.add("trusted ctagPcp");
                if( !bridgePortSettings.contains("user-priority-mapping 0 0")) settingsComment.add("user-priority-mapping 0 0");
                if( !bridgePortSettings.contains("user-priority-mapping 1 1")) settingsComment.add("user-priority-mapping 1 1");
                if( !bridgePortSettings.contains("user-priority-mapping 2 2")) settingsComment.add("user-priority-mapping 2 2");
                if( !bridgePortSettings.contains("user-priority-mapping 3 3")) settingsComment.add("user-priority-mapping 3 3");
                if( !bridgePortSettings.contains("user-priority-mapping 4 4")) settingsComment.add("user-priority-mapping 4 4");
                if( !bridgePortSettings.contains("user-priority-mapping 5 5")) settingsComment.add("user-priority-mapping 5 5");
                if( !bridgePortSettings.contains("user-priority-mapping 6 6")) settingsComment.add("user-priority-mapping 6 6");
                if( !bridgePortSettings.contains("user-priority-mapping 7 7")) settingsComment.add("user-priority-mapping 7 7");
                if( !bridgePortSettings.contains("deep-buffering")) settingsComment.add("deep-buffering");
                if( !bridgePortSettings.contains("scheduler-profile 10")) settingsComment.add("scheduler-profile 10");
                if( !bridgePortSettings.contains("queue-set-profile 5")) settingsComment.add("queue-set-profile 5");

                if( settingsComment.size() > 0 ) comments.add(String.format("[%s: %s]", pair.getKey(), String.join(",", settingsComment)));
            }
        }

        return comments.size() > 0 ? String.join(", ", comments) : "";
    }

//    private String assessBridgePort(String bridgePortPrint) {
//        List<String> comments = new ArrayList<>();
//        Map<String, String> bridgePorts = new HashMap<>();
//
//        Pattern regex = Pattern.compile("!LAN (?<interface>[\\d+\\/]+)(?<settings>.*?)interface ethernet", Pattern.DOTALL);
//        Matcher matcher = regex.matcher(bridgePortPrint);
//        while (matcher.find()) {
//            String lanInterface = matcher.group("interface");
//            String settings = matcher.group("settings");
//
//            bridgePorts.put(lanInterface, settings);
//        }
//
//        Iterator<Map.Entry<String, String>> it = bridgePorts.entrySet().iterator();
//        while (it.hasNext()) {
//            Map.Entry<String, String> pair = it.next();
//            it.remove(); // avoids a ConcurrentModificationException
//
//            List<String> bridgePortSettings = new ArrayList<>();
//            for(String line : pair.getValue().split("\\r\\n|\\r|\\n")) {
//                bridgePortSettings.add(line.trim());
//            }
//            List<String> settingsComment = new ArrayList<>();
//            if( !bridgePortSettings.contains("role uni")) settingsComment.add("role uni");
//            if( !bridgePortSettings.contains("bridge-port")) settingsComment.add("bridge-port");
//            if( !bridgePortSettings.contains("maxfs 2000")) settingsComment.add("maxfs 2000");
//            if( !bridgePortSettings.contains("no mac-whitelist")) settingsComment.add("no mac-whitelist");
//            if( !bridgePortSettings.contains("admit untagged priority-tagged vlan-tagged")) settingsComment.add("admit untagged priority-tagged vlan-tagged");
//            if( !bridgePortSettings.contains("pvid 0")) settingsComment.add("pvid 0");
//            if( !bridgePortSettings.contains("no stormctrl bc")) settingsComment.add("no stormctrl bc");
//            if( !bridgePortSettings.contains("no stormctrl mc")) settingsComment.add("no stormctrl mc");
//            if( !bridgePortSettings.contains("no stormctrl dlf")) settingsComment.add("no stormctrl dlf");
//            if( !bridgePortSettings.contains("stormctrl maxbcbw 100")) settingsComment.add("stormctrl maxbcbw 100");
//            if( !bridgePortSettings.contains("stormctrl maxmcbw 100")) settingsComment.add("stormctrl maxmcbw 100");
//            if( !bridgePortSettings.contains("stormctrl maxdlfbw 100")) settingsComment.add("stormctrl maxdlfbw 100");
//            if( !bridgePortSettings.contains("no max-learned-addresses")) settingsComment.add("no max-learned-addresses");
//            if( !bridgePortSettings.contains("no forbidden-egressports")) settingsComment.add("no forbidden-egressports");
//            if( !bridgePortSettings.contains("l2cp lldp discard")) settingsComment.add("l2cp lldp discard");
//            if( !bridgePortSettings.contains("l2cp esmc discard")) settingsComment.add("l2cp esmc discard");
//            if( !bridgePortSettings.contains("default-network-priority 0")) settingsComment.add("default-network-priority 0");
//            if( !bridgePortSettings.contains("trusted ctagPcp")) settingsComment.add("trusted ctagPcp");
//            if( !bridgePortSettings.contains("user-priority-mapping 0 0")) settingsComment.add("user-priority-mapping 0 0");
//            if( !bridgePortSettings.contains("user-priority-mapping 1 1")) settingsComment.add("user-priority-mapping 1 1");
//            if( !bridgePortSettings.contains("user-priority-mapping 2 2")) settingsComment.add("user-priority-mapping 2 2");
//            if( !bridgePortSettings.contains("user-priority-mapping 3 3")) settingsComment.add("user-priority-mapping 3 3");
//            if( !bridgePortSettings.contains("user-priority-mapping 4 4")) settingsComment.add("user-priority-mapping 4 4");
//            if( !bridgePortSettings.contains("user-priority-mapping 5 5")) settingsComment.add("user-priority-mapping 5 5");
//            if( !bridgePortSettings.contains("user-priority-mapping 6 6")) settingsComment.add("user-priority-mapping 6 6");
//            if( !bridgePortSettings.contains("user-priority-mapping 7 7")) settingsComment.add("user-priority-mapping 7 7");
//            if( !bridgePortSettings.contains("deep-buffering")) settingsComment.add("deep-buffering");
//            if( !bridgePortSettings.contains("scheduler-profile 10")) settingsComment.add("scheduler-profile 10");
//            if( !bridgePortSettings.contains("queue-set-profile 5")) settingsComment.add("queue-set-profile 5");
//
//            if( settingsComment.size() > 0 ) comments.add(String.format("[%s: %s]", pair.getKey(), String.join(",", settingsComment)));
//        }
//
//        return comments.size() > 0 ? String.join(", ", comments) : "";
//    }
//
}

class MiniLinkRowData {
    String ip;
    String name;
    Map<String, String> rawData;

    public MiniLinkRowData(String name, String iPv4, Map<String, String> rawData) {
        this.name = name;
        this.ip = iPv4;
        this.rawData = rawData;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, String> getRawData() {
        return rawData;
    }

    public void setRawData(Map<String, String> rawData) {
        this.rawData = rawData;
    }
}