package com.scenemax.desktop;

import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPDataTransferListener;
import net.lingala.zip4j.core.ZipFile;
import net.lingala.zip4j.exception.ZipException;
import net.lingala.zip4j.model.ZipParameters;
import okhttp3.*;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import javax.imageio.ImageIO;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;
import javax.swing.*;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static java.lang.System.getProperty;

public class Util {

    private static final String APPLICATION_VERSION = "2.0.1";
    private static final int DIALOG_TEXT_MAX_CHARS = 32000;
    private static final int DIALOG_TEXT_AREA_WIDTH = 900;
    private static final int DIALOG_TEXT_AREA_HEIGHT = 520;

    /**
     * Applies the Monokai dark theme to an RSyntaxTextArea so it matches FlatDarkLaf.
     */
    public static void applyDarkTheme(RSyntaxTextArea textArea) {
        try {
            Theme theme = Theme.load(Util.class.getResourceAsStream(
                    "/org/fife/ui/rsyntaxtextarea/themes/monokai.xml"));
            theme.apply(textArea);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Loads an icon from the classpath and makes near-white pixels fully transparent,
     * so icons with baked-in white backgrounds look correct on dark themes.
     */
    public static ImageIcon loadIconTransparent(String resourcePath) {
        try {
            BufferedImage src = ImageIO.read(Util.class.getResourceAsStream(resourcePath));
            BufferedImage dst = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            WritableRaster srcRaster = src.getRaster();
            WritableRaster dstRaster = dst.getRaster();
            int[] pixel = new int[4];
            for (int y = 0; y < src.getHeight(); y++) {
                for (int x = 0; x < src.getWidth(); x++) {
                    srcRaster.getPixel(x, y, pixel);
                    int r = pixel[0], g = pixel[1], b = pixel[2];
                    int a = (src.getColorModel().getNumComponents() == 4) ? pixel[3] : 255;
                    // treat near-white (all channels >= 240) as transparent
                    if (r >= 240 && g >= 240 && b >= 240) {
                        a = 0;
                    }
                    dstRaster.setPixel(x, y, new int[]{r, g, b, a});
                }
            }
            return new ImageIcon(dst);
        } catch (Exception e) {
            // fallback: return the icon as-is
            URL url = Util.class.getResource(resourcePath);
            return url != null ? new ImageIcon(url) : null;
        }
    }

    public static void showScrollableMessageDialog(Component parent, String message, String title, int messageType) {
        JTextArea textArea = new JTextArea(limitDialogText(message));
        textArea.setEditable(false);
        textArea.setLineWrap(false);
        textArea.setWrapStyleWord(false);
        textArea.setCaretPosition(0);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(DIALOG_TEXT_AREA_WIDTH, DIALOG_TEXT_AREA_HEIGHT));

        JOptionPane.showMessageDialog(parent, scrollPane, title, messageType);
    }

    private static String limitDialogText(String message) {
        String text = message == null || message.isBlank() ? "<no details available>" : message;
        if (text.length() <= DIALOG_TEXT_MAX_CHARS) {
            return text;
        }

        return text.substring(0, DIALOG_TEXT_MAX_CHARS)
                + System.lineSeparator()
                + System.lineSeparator()
                + "[Output truncated. See the full log for complete details.]";
    }

    public static final int MAX_UPLOAD_GAME_SIZE = 200;

    public static String FTP_HOST_NAME;
    public static String FTP_PASSWORD;
    public static String FTP_USER_NAME;
    public static int FTP_PORT = 21;
    public static String FILE_TRANSFER_PROTOCOL = "FTP";
    public static String CONSUMER_SECRET;
    public static String CONSUMER_KEY;
    public static String WRITE_CONSUMER_SECRET;
    public static String WRITE_CONSUMER_KEY;
    public static String FTP_UPLOAD_URL;
    public static String GALLERY_HOST;
    private static boolean licenseExists = false;
    private static int whiteLableCode=0;

    public static String getAppVersion() {
        return APPLICATION_VERSION;
    }

    public static synchronized String getPlatform() {

        String platform = null;

        if (platform == null) {
            String model = getProperty("sun.arch.data.model");
            String os = getProperty("os.name").toLowerCase();
            if (os.startsWith("windows")) {
                platform = "w";
            } else {
                if (!os.equals("linux")) {
                    System.out.println(os);
                    throw new UnsupportedOperationException("Platform not supported " + os);
                }

                platform = "l";
            }

            platform = platform + model;
        }

        return platform;

    }


    ///////////////// NEW PROJECTS SYSTEM /////////

    public static JSONObject getProjectsConfig() {
        File f = new File("projects/projects.json");
        if(!f.exists()) {
            return null;
        }

        String projectsSetup = null;
        try {
            projectsSetup = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        JSONObject obj = new JSONObject(projectsSetup);

        return obj;
    }

    public static String getDefaultResourcesFolder() {
        return "./resources";
    }

    public static String getResourcesFolder() {
        SceneMaxProject p = getActiveProject();
        return p.getResourcesPath();
    }

    public static String getScriptsFolder() {
        SceneMaxProject p = getActiveProject();
        return p.getScriptsPath();
    }

    public static SceneMaxProject getActiveProject() {

        List<SceneMaxProject> projects = getProjects_New();
        JSONObject obj = getProjectsConfig();
        if(obj==null || projects.size()==0) {
            return null;
        }

        String selectedProject = obj.getString("selectedProject");

        for(SceneMaxProject p : projects) {
            if(p.name.equals(selectedProject)) {
                return p;
            }
        }

        return null;
    }


    public static List<SceneMaxProject> getProjects_New() {

        List<SceneMaxProject> projects=new ArrayList<>();
        File f = new File("projects");
        if(!f.exists()) {
            return projects;
        }

        JSONObject obj = getProjectsConfig();
        if(obj==null) {
            return projects;
        }

        JSONArray j = obj.getJSONArray("projects");
        for(int i=0;i<j.length();++i) {
            JSONObject o = j.getJSONObject(i);
            SceneMaxProject p  = new SceneMaxProject();
            p.name=o.getString("name");
            p.path=o.getString("path");

            if(o.has("selectedParent")) {
                p.selectedParent = o.getString("selectedParent");
            }

            if(o.has("selectedNode")) {
                p.selectedNode = o.getString("selectedNode");
            }

            p.itchGamePage = o.optString("itchGamePage", "");
            p.itchButlerPath = o.optString("itchButlerPath", "");
            p.itchWindowsChannel = o.optString("itchWindowsChannel", "");
            p.itchLinuxChannel = o.optString("itchLinuxChannel", "");
            p.itchMacChannel = o.optString("itchMacChannel", "");

            projects.add(p);
        }

        return projects;

    }


    public static boolean createProject(String name, String defaultFolderName) {

        File projectsFolder = new File("projects");
        if(!projectsFolder.exists()) {
            projectsFolder.mkdir();
        }

        name=name.trim();

        File f = new File("projects/projects.json");
        String projectsSetup = "{\"projects\":[]}";
        if(f.exists()) {
            try {
                projectsSetup = FileUtils.readFileToString(f,StandardCharsets.UTF_8);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        JSONObject obj = new JSONObject(projectsSetup);
        JSONArray arr = obj.getJSONArray("projects");

        for(int i=0;i<arr.length();++i) {
            JSONObject proj = arr.getJSONObject(i);
            String projName = proj.getString("name");
            if(name.equals(projName)) {
                JOptionPane.showMessageDialog(null,"Project creation failed. Try again with a different project name","Project Folder Already exists",JOptionPane.INFORMATION_MESSAGE);
                return false;// project already exist
            }
        }


        String folderName = name.replace(" ","_");
        File file = new File("projects/"+folderName);
        file.mkdirs();
        file = new File("projects/"+folderName+"/resources");
        file.mkdirs();
        file = new File("projects/"+folderName+"/resources/Models");
        file.mkdirs();
        file = new File("projects/"+folderName+"/resources/sprites");
        file.mkdirs();
        file = new File("projects/"+folderName+"/resources/skyboxes");
        file.mkdirs();
        file = new File("projects/"+folderName+"/resources/audio");
        file.mkdirs();
        file = new File("projects/"+folderName+"/resources/fonts");
        file.mkdirs();

        file = new File("projects/"+folderName+"/scripts");
        file.mkdirs();

        // create default folder
        String mainFileContent = "";
        if(defaultFolderName==null) {
            defaultFolderName="hello world";
            mainFileContent = "sys.print \"Hello World!\"\r\n" +
                    "skybox.show solar system\r\n" +
                    "d is a dragon; d.fly loop";
        }
        file = new File("projects/"+folderName+"/scripts/"+defaultFolderName);
        file.mkdirs();
        File mainFile = new File("projects/"+folderName+"/scripts/"+defaultFolderName+"/main");

        try {
            FileUtils.write(mainFile, mainFileContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }

        JSONObject pr = new JSONObject();
        pr.put("name",name);
        pr.put("path","projects/"+folderName);
        pr.put("selectedParent",defaultFolderName);
        pr.put("selectedNode","main");
        arr.put(pr);

        obj.put("selectedProject",name);
        obj.put("projects",arr);

        try {
            FileUtils.write(f,obj.toString(4),StandardCharsets.UTF_8);

        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        AppDB.getInstance().setParam("selected_tree_node_parent", defaultFolderName);
        AppDB.getInstance().setParam("selected_tree_node", "main");

        return true;
    }


    public static boolean switchProject(String name) {

        SceneMaxProject currProj = getActiveProject();
        if(name.equals(currProj.name)) {
            return true;
        }

        String parent = AppDB.getInstance().getParam("selected_tree_node_parent");
        String node = AppDB.getInstance().getParam("selected_tree_node");

        currProj.selectedParent=parent;
        currProj.selectedNode=node;
        saveProject(currProj);

        List<SceneMaxProject> projects = getProjects_New();
        for(SceneMaxProject p : projects) {
            if(p.name.equals(name)) {
                AppDB.getInstance().setParam("selected_tree_node_parent", p.selectedParent);
                AppDB.getInstance().setParam("selected_tree_node", p.selectedNode);

                File f = new File("projects/projects.json");
                String projectsSetup = "{\"projects\":[]}";
                if(f.exists()) {
                    try {
                        projectsSetup = FileUtils.readFileToString(f,StandardCharsets.UTF_8);

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                JSONObject obj = new JSONObject(projectsSetup);
                obj.put("selectedProject",name);
                try {
                    FileUtils.write(f,obj.toString(4),StandardCharsets.UTF_8);
                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }

                return true;
            }
        }

        return false;

    }

    private static boolean saveProject(SceneMaxProject p) {

        JSONObject conf = getProjectsConfig();
        JSONArray projects = conf.getJSONArray("projects");
        for(int i=0;i<projects.length();++i) {
            JSONObject obj = projects.getJSONObject(i);
            if(obj.getString("name").equals(p.name)) {
                obj.put("selectedParent",p.selectedParent);
                obj.put("selectedNode",p.selectedNode);
                obj.put("itchGamePage", safeProjectValue(p.itchGamePage));
                obj.put("itchButlerPath", safeProjectValue(p.itchButlerPath));
                obj.put("itchWindowsChannel", safeProjectValue(p.itchWindowsChannel));
                obj.put("itchLinuxChannel", safeProjectValue(p.itchLinuxChannel));
                obj.put("itchMacChannel", safeProjectValue(p.itchMacChannel));

                try {
                    File f = new File("projects/projects.json");
                    FileUtils.write(f,conf.toString(4),StandardCharsets.UTF_8);

                } catch (IOException e) {
                    e.printStackTrace();
                    return false;
                }
            }
        }

        return true;

    }

    public static boolean saveProjectSettings(SceneMaxProject project) {
        if (project == null) {
            return false;
        }
        return saveProject(project);
    }

    private static String safeProjectValue(String value) {
        return value == null ? "" : value.trim();
    }

    private static String getProjectScopedKey(SceneMaxProject project, String suffix) {
        if (project == null || project.name == null) {
            return suffix;
        }
        return "project~" + project.name + "~" + suffix;
    }

    public static String getProjectItchApiKey(SceneMaxProject project) {
        String value = AppDB.getInstance().getParam(getProjectScopedKey(project, "itch_api_key"));
        return value == null ? "" : value.trim();
    }

    public static void setProjectItchApiKey(SceneMaxProject project, String apiKey) {
        AppDB.getInstance().setParam(getProjectScopedKey(project, "itch_api_key"), apiKey == null ? "" : apiKey.trim());
    }

    ////////////////////////////////

    private static void updateProjectAttrFile(File f, SceneMaxProject p) {

        JSONObject attr = p.toJSON();
        try {
            FileUtils.write(new File(f.getAbsolutePath() + "\\attr.json"), attr.toString(4), StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static SceneMaxProject createProjectAttrFile(File projectFolder, JSONObject attr) {

        String name=attr.getString("name");

        try {
            FileUtils.write(new File(projectFolder.getAbsolutePath() + "\\attr.json"), attr.toString(4), StandardCharsets.UTF_8);
            SceneMaxProject p = new SceneMaxProject();
            p.name=name;
            p.path=projectFolder.getAbsolutePath();
            p.selectedParent=attr.getString("selected_parent");
            p.selectedNode=attr.getString("selected_node");
            return p;
        } catch(Exception e) {
            e.printStackTrace();
        }

        return null;

    }


    public static List<SceneMaxProject> getProjects() {
        List<SceneMaxProject> projects = new ArrayList<>();
        File folder = new File(".");
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.isDirectory()) {
                SceneMaxProject p = getProjectAttributes(fileEntry);
                if(p!=null) {
                    projects.add(p);
                }
            }
        }

        return projects;
    }

    public static SceneMaxProject getProjectAttributes(File fileEntry) {
        File attr = FileUtils.getFile(fileEntry,"attr.json");
        if(attr.exists()) {
            try {
                String cont = FileUtils.readFileToString(attr,StandardCharsets.UTF_8);
                JSONObject json = new JSONObject(cont);
                SceneMaxProject p = new SceneMaxProject();
                p.path=fileEntry.getAbsolutePath();
                p.name=json.getString("name");

                try {
                    p.selectedParent = json.getString("selected_parent");
                    p.selectedNode = json.getString("selected_node");
                } catch(JSONException e) {
                    //e.printStackTrace();
                }

                return p;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    public static void zipFolder(String path) {

        try {
            File folder = new File(path);
            ZipFile zipFile = new ZipFile(folder.getAbsolutePath()+".zip");
            ZipParameters zp = new ZipParameters();
            zp.setIncludeRootFolder(false);
            zipFile.addFolder(folder, zp);
        } catch (ZipException e) {
            e.printStackTrace();

        }
    }

    public static String unzip(String source, String destination){

        try {
            ZipFile zipFile = new ZipFile(source);
            zipFile.extractAll(destination);
        } catch (ZipException e) {
            e.printStackTrace();
            return e.getMessage();
        }

        return "";
    }

    public static String readFile(File f) {

        String text = "";
        try {
            text = FileUtils.readFileToString(f,StandardCharsets.UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return text;


    }

    public static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[0x1000];
        while (true) {
            int r = in.read(buf);
            if (r == -1) {
                break;
            }
            out.write(buf, 0, r);
        }
        return out.toByteArray();
    }

    public static boolean writeFile(String path, String text) {

        BufferedWriter writer = null;
        try {
            File target = new File(path);
            writer = new BufferedWriter(new FileWriter(target));
            writer.write(text);
            writer.close();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }

        return false;

    }

    public static String downloadFile(String url) {

        try {
            URL website = new URL(url);
            String fileName = new File(website.getFile()).getName();
            ReadableByteChannel rbc = Channels.newChannel(website.openStream());
            File f = new File("tmp/"+fileName);//
            FileOutputStream fos = new FileOutputStream(f);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.flush();
            fos.close();
            return f.getAbsolutePath();
        } catch(Exception e) {

            return "error: "+e.getClass().getName()+" - " + e.getMessage();
        }

    }


    private static List<ConnectionSpec> createConnectionSpecs() {
        ConnectionSpec spec = new ConnectionSpec.Builder(ConnectionSpec.MODERN_TLS)
                .tlsVersions(TlsVersion.TLS_1_2)
                .cipherSuites(
                        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,
                        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA,
                        CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,
                        CipherSuite.TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,
                        CipherSuite.TLS_ECDHE_ECDSA_WITH_RC4_128_SHA,
                        CipherSuite.TLS_ECDHE_RSA_WITH_RC4_128_SHA,
                        CipherSuite.TLS_DHE_RSA_WITH_AES_128_CBC_SHA,
                        CipherSuite.TLS_DHE_DSS_WITH_AES_128_CBC_SHA,
                        CipherSuite.TLS_DHE_RSA_WITH_AES_256_CBC_SHA)
                .build();
        return Collections.singletonList(spec);
    }

    public static String getKeyByScriptPath(String scriptPath) {

        File scriptFile = new File(scriptPath);
        SceneMaxProject p = getActiveProject();
        String key = p.name + "~" + scriptFile.getParentFile().getName() + "~" + scriptFile.getName();
        return key;
    }

    public static void setScriptJsonParams(String scriptPath, JSONObject params) {

        String key = Util.getKeyByScriptPath(scriptPath);
        AppDB.getInstance().setParam(key, params.toString());
    }

    public static JSONObject getScriptJsonParams(String scriptPath) {

        String key = Util.getKeyByScriptPath(scriptPath);

        String fileParams = AppDB.getInstance().getParam(key);
        JSONObject params=null;
        if(fileParams!=null && fileParams.length()>0) {
            params=new JSONObject(fileParams);
        } else {
            params=new JSONObject();
            params.put("key",key);
        }

        return params;
    }

    public static String getAppName() {
        return "SceneMax3D Developer Studio";
    }

    public static String getAppTitle(boolean licenseExists) {

        SceneMaxProject p = Util.getActiveProject();
        String projName = "";
        if(p!=null) {
            projName=" - "+p.name;
        }
        String name=getAppName();
        String ver=getAppVersion();
        return name + " ("+ver+") "+projName;

    }

    public static boolean getLicenseExists() {
        return licenseExists;
    }

    public static void setLicenseExists(boolean licenseExists, int whiteLableCode) {
        Util.licenseExists=licenseExists;
        Util.whiteLableCode=whiteLableCode;
    }

    public static String getAppEmail() {
        return "scenemax3d@gmail.com";
    }

    public static String getAppWebsite() {
        return "www.scenemax3d.com";
    }

    public static String getAppLogo() {
        return "/images/scenemax_icon.png";
    }

    public static String getOnlineHelpUrl() {
        return "https://scenemax3d.com/knowledge-base/";
    }

    public static String getWorkingDir() {
        return System.getProperty("user.dir");
    }

    public static String getResourcePath(String path) {

        // first check the project folder
        String resPath = "./" + Util.getResourcesFolder()+"/"+path;
        File f = new File(resPath);
        if(f.exists()) {
            return resPath;
        }

        // fallback to the default resources folder
        resPath = "./resources/"+path;
        return resPath;

    }

    public static String getStationId() {

        String id = AppDB.getInstance().getParam("station_id");

        if (id == null || id.length() == 0) {
            String homePath = System.getProperty("user.home");
            File f = new File(homePath, "station_id");
            if (f.exists()) {
                try {
                    id = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            if (id == null || id.length() == 0) {
                id = java.util.UUID.randomUUID().toString();
                try {
                    FileUtils.write(f, id, StandardCharsets.UTF_8);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            AppDB.getInstance().setParam("station_id", id);
        }

        return id;
    }

    public static String getProgramHubId(String scriptPath) {

        JSONObject params = getScriptJsonParams(scriptPath);
        String hubId = null;

        if (params.has("hubId")) {
            hubId = params.getString("hubId");
        }

        if (hubId == null || hubId.length() == 0) {
            hubId = RandomStringUtils.random(8, "0123456789abcdef");
            params.put("hubId", hubId);
            Util.setScriptJsonParams(scriptPath, params);
        }

        return hubId;
    }

    static class ResponseInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {

            Response response = chain.proceed(chain.request());
            Response modified = response.newBuilder()
                    .addHeader("Content-Type", "application/json; charset=utf-8")
                    .build();

            return modified;
        }

    }

//    private static String doPutRequest(String url, String body, String[] queryParams) {
//
//        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
//
//        OkHttpClient client = createHttpClient();
//        String urlWithQs = createUrl(url,queryParams,true);
//        //RequestBody rb = RequestBody.create(body,JSON);
//        RequestBody rb = RequestBody.create(JSON,body);
//        Request request = new Request.Builder()
//                .url(urlWithQs)
//                .put(rb)
//                .addHeader("Content-Type","application/json; charset=utf-8")
//                .build();
//
//        String retval = callAndGetResponse(client,request);
//        return retval;
//
//    }

//    private static String doPostRequest(String url, String body, String[] queryParams) {
//
//        MediaType JSON = MediaType.parse("application/json; charset=utf-8");
//
//        OkHttpClient client = createHttpClient();
//        String urlWithQs = createUrl(url,queryParams,true);
//        //RequestBody rb = RequestBody.create(body,JSON);
//        RequestBody rb = RequestBody.create(JSON,body);
//        Request request = new Request.Builder()
//                .url(urlWithQs)
//                .post(rb)
//                .addHeader("Content-Type","application/json; charset=utf-8")
//                .build();
//
//        String retval = callAndGetResponse(client,request);
//        return retval;
//
//    }

    public static String doHttpsPutRequest(String url,String body, String[] queryParams) {
        return execConn(url,"PUT",queryParams,body);
    }

    public static String doHttpsPostRequest(String url,String body, String[] queryParams) {
        return execConn(url,"POST",queryParams,body);
    }

    public static String doHttpPostRequest2(String url,String body, String[] queryParams) {
        return execHttpConn("$"+url,"POST",queryParams,body);
    }


    public static String doHttpGetRequest2(String url, String[] queryParams) {
        return execHttpConn("$"+url,"GET",queryParams,null);
    }

    public static String doHttpsGetRequest(String url, String[] queryParams) {
        return execConn(url,"GET",queryParams,null);
    }

    private static String execHttpConn(String url, String method, String[] queryParams, String body) {

        try {

            boolean useConsumerKey = true;
            if(url.startsWith("$")) {
                url=url.replaceFirst("\\$","");
                useConsumerKey=false;
            }
            String urlWithQs = createUrl(url,queryParams,false, useConsumerKey);

            URL myUrl = new URL(urlWithQs);
            HttpURLConnection conn = (HttpURLConnection) myUrl.openConnection();

            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            conn.setRequestProperty("accept", "application/json");
            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            if(body!=null) {

                try(OutputStream os = conn.getOutputStream()) {
                    byte[] input = body.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

            }

            conn.connect();

            InputStream is = conn.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            String inputLine;
            StringBuilder retval= new StringBuilder();

            while (true) {

                inputLine = br.readLine();
                if(inputLine==null) {
                    break;
                }

                retval.append(inputLine);

            }

            return retval.toString();


        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private static String execConn(String url, String method, String[] queryParams, String body) {

        try {

            boolean useConsumerKey = true;
            if(url.startsWith("$")) {
                url=url.replaceFirst("\\$","");
                useConsumerKey=false;
            }
            String urlWithQs = createUrl(url,queryParams,false, useConsumerKey);

            URL myUrl = new URL(urlWithQs);
            HttpsURLConnection conn = (HttpsURLConnection) myUrl.openConnection();

            conn.setRequestMethod(method);
            conn.setRequestProperty("Content-Type", "application/json;charset=utf-8");
            conn.setRequestProperty("accept", "application/json");
            //conn.setRequestProperty("Content-Length", "" + postData.getBytes().length);

            conn.setUseCaches(false);
            conn.setDoInput(true);
            conn.setDoOutput(true);

            if(body!=null) {

                try(OutputStream os = conn.getOutputStream()) {
                    byte[] input = body.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

            }

            conn.connect();

            InputStream is = conn.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            String inputLine;
            StringBuilder retval= new StringBuilder();

            while (true) {

                inputLine = br.readLine();
                if(inputLine==null) {
                    break;
                }

                retval.append(inputLine);

            }

            return retval.toString();


        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

//    private static String doGetRequest(String url, String[] queryParams) {
//
//        OkHttpClient client = createHttpClient();
//        String urlWithQs = createUrl(url,queryParams,false);
//
//        Request request = new Request.Builder()
//                .url(urlWithQs)
//                .addHeader("Content-Type","application/json; charset=utf-8")
//                .build();
//
//
//        String retval = callAndGetResponse(client,request);
//        return retval;
//
//    }

    private static OkHttpClient createHttpClient() {
        System.setProperty("https.protocols", "TLSv1.2");//TLSv1,TLSv1.1,
        OkHttpClient client = new OkHttpClient.Builder()
                .connectionSpecs(Arrays.asList(ConnectionSpec.MODERN_TLS, ConnectionSpec.COMPATIBLE_TLS))
                .build();

        return client;
    }

    private static String callAndGetResponse(OkHttpClient client, Request request) {

        Response response = null;
        try {
            response = client.newCall(request).execute();

            String retval = response.body().source().readUtf8();//  toString();
            return retval;

        } catch (IOException e) {
            e.printStackTrace();

        }

        return null;

    }

    private static String createUrl(String url, String[] queryParams, boolean writePermissions, boolean useConsumerKey) {

        HttpUrl.Builder urlBuilder = HttpUrl.parse(url).newBuilder();

        if(useConsumerKey) {
            if (writePermissions) {
                urlBuilder.addQueryParameter("consumer_key", WRITE_CONSUMER_KEY);
                urlBuilder.addQueryParameter("consumer_secret", WRITE_CONSUMER_SECRET);
            } else {
                urlBuilder.addQueryParameter("consumer_key", CONSUMER_KEY);
                urlBuilder.addQueryParameter("consumer_secret", CONSUMER_SECRET);
            }
        }

        if(queryParams!=null) {
            for (int i = 0; i < queryParams.length; ++i) {
                urlBuilder.addQueryParameter(queryParams[i], queryParams[i + 1]);
                i++;
            }
        }

        String urlWithQs = urlBuilder.build().toString();

        return urlWithQs;
    }

    public static String createOrUpdateProduct(WooProduct item,String scriptPath, String prevSku) {

        String retval = null;

        WooProduct currProd = null;
        boolean allowChangeSku = false; // allow change sku only if it's not exist

        WooProduct prodBySku = getProductFromWebStore("sku",item.sku);
        if(prodBySku==null) {
            allowChangeSku=true; // ensure no other product with the same sku
        }

        if(item.webId!=null) {
            currProd = getProductFromWebStore("id",item.webId);

        } else {
            currProd = prodBySku;//getProductFromWebStore("sku",item.sku);
        }


        if(currProd==null) {
            String url = GALLERY_HOST+"/wp-json/wc/v3/products";
            JSONObject json = new JSONObject("{\"categories\":[{\"id\":61}] }");
            json.put("name", item.name);
            json.put("catalog_visibility", item.visibility);
            json.put("sku", item.sku);
            json.put("downloadable", true);
            json.put("type", "simple");
            json.put("description", item.desc);
            json.put("short_description", item.shortDesc);
            JSONArray downloads = new JSONArray();
            JSONObject file = new JSONObject();
            file.put("name", item.name);
            file.put("file", FTP_UPLOAD_URL + item.downloadFile);
            downloads.put(file);
            json.put("downloads", downloads);

            if(item.downloadFilePic!=null) {
                JSONArray images = new JSONArray();
                JSONObject image = new JSONObject();
                String imageSrc;
                imageSrc=FTP_UPLOAD_URL+item.downloadFilePic;
                image.put("src", imageSrc);
                images.put(image);
                json.put("images", images);

            } else {
                //imageSrc=GALLERY_HOST+"/wp-content/uploads/2020/03/tom_mouse2-e1584475973624.png";
            }

            //retval = doPostRequest(url, json.toString(), null);
            retval = doHttpsPostRequest(url, json.toString(), null);


            JSONObject obj = null;
            WooProduct wp = null;

            try {
                obj = new JSONObject(retval); // in case of post error it will be null
                wp = new WooProduct(obj);
            } catch (Exception e) {
                wp = new WooProduct(obj);
                wp.downloadFile=FTP_UPLOAD_URL+item.downloadFilePic;
                wp.sku=item.sku;
            }

            Util.relateProductToClient(wp);
            obj.put("localFilePic",wp.localFilePic);
            retval = obj.toString();
            Util.updateScriptWebParams(scriptPath,retval);

        } else {
            // update only if this sku belongs to this client
            if(Util.isProductIdBelongsToClient(currProd.downloadFileId)) {

                if(!allowChangeSku && !currProd.downloadFileId.equals(item.downloadFileId)) {
                    return "Error: Cannot use this SKU. It belongs to another product";
                }

                String url = GALLERY_HOST+"/wp-json/wc/v3/products/"+currProd.webId;
                JSONObject json = new JSONObject();
                json.put("name", item.name);
                if(allowChangeSku) {
                    json.put("sku", item.sku);
                }
                json.put("description", item.desc);
                json.put("short_description", item.shortDesc);
                json.put("catalog_visibility", item.visibility);

                if(item.downloadFilePic!=null) {
                    JSONArray images = new JSONArray();
                    JSONObject image = new JSONObject();
                    String imageSrc;
                    imageSrc=GALLERY_HOST+"/wp-content/downloads/"+item.downloadFilePic;
                    image.put("src", imageSrc);
                    images.put(image);
                    json.put("images", images);
                } else {
                    //imageSrc=GALLERY_HOST+"/wp-content/uploads/2020/03/tom_mouse2-e1584475973624.png";
                }

                retval = doHttpsPutRequest(url,json.toString(),null);
                if(retval==null) {
                    return "Error: Failed updating the product. Probably a network error";
                }
                JSONObject obj = new JSONObject(retval);
                obj.put("localFilePic",item.localFilePic);
                retval=obj.toString();
                Util.updateScriptWebParams(scriptPath,retval);
            } else {
                return "Error: Cannot use this SKU. It belongs to another product";
            }

        }

        return retval;
    }

    private static boolean isProductIdBelongsToClient(String prodId) {

        JSONObject products = Util.getClientRelatedProductKeys();
        return (products.has(prodId));

    }

    private static void updateScriptWebParams(String scriptPath, String product) {
        //
        JSONObject obj = new JSONObject((product));
        JSONObject params = Util.getScriptJsonParams(scriptPath);
        String id = String.valueOf(obj.getInt("id"));
        String sku = obj.getString("sku");
        String webName = obj.getString("name");
        String webDesc = obj.getString("description");
        String webVisible = obj.getString("catalog_visibility");

        params.put("webId",id);
        params.put("webName",webName);
        params.put("webDesc",webDesc);
        params.put("webSku",sku);
        params.put("webVisible",webVisible);
        if(obj.has("localFilePic")) {
            params.put("localFilePic",obj.getString("localFilePic"));
        }
        Util.setScriptJsonParams(scriptPath,params);
    }


    private static void relateProductToClient(WooProduct prod) {

        if(prod.json ==null) {
            return;
        }

        String id = prod.downloadFileId;//
        String sku = prod.sku;

        JSONObject products = Util.getClientRelatedProductKeys();

        if(!products.has(id)) {
            products.put(id,sku);
        }

        Util.setClientRelatedProductKeys(products);

    }

    private static void setClientRelatedProductKeys(JSONObject products) {
        AppDB.getInstance().setParam("client_product_keys",products.toString());

    }

    private static JSONObject getClientRelatedProductKeys() {

        JSONObject obj = null;
        String keys = AppDB.getInstance().getParam("client_product_keys");
        if(keys!=null) {
            obj = new JSONObject(keys);
        } else {
            obj=new JSONObject();
        }

        return obj;
    }

    private static String getThisClientKey() {
        String key = AppDB.getInstance().getParam("client_key");
        if(key==null || key.length()==0) {
            key=java.util.UUID.randomUUID().toString();
            AppDB.getInstance().setParam("client_key",key);
        }

        return key;
    }

    public static WooProduct getProductFromWebStore(String filterKey, String filterVal) {

        WooProduct p = null;
        JSONObject item = null;

        if(filterKey.equals("id")) {
            item = getProductById(filterVal);
        } else {
            item = getProduct(filterKey,filterVal);
        }

        if(item!=null) {

            p = new WooProduct(item);

        }

        return p;

    }

    public static JSONObject getProductById(String id) {

        String url = GALLERY_HOST+"/wp-json/wc/v3/products/"+id;
        String product = doHttpsGetRequest(url,null );

        try {
            JSONObject item = new JSONObject(product);
            return item;
        }catch(Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static JSONObject getProduct(String filterKey, String filterVal) {

        String url = GALLERY_HOST+"/wp-json/wc/v3/products";
        String product = doHttpsGetRequest(url,new String[] {filterKey,filterVal} );

        try {
            JSONArray arr = new JSONArray(product);
            if(arr!=null && arr.length()>0) {
                return arr.getJSONObject(0);
            }

        }catch(Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    public static void ftpUploadFile(File path, FTPDataTransferListener listener) {

        FTPClient client = new FTPClient();

        String USERNAME = FTP_USER_NAME;
        String PASSWORD = FTP_PASSWORD;
        String FTP_HOST = FTP_HOST_NAME;

        try {

            client.connect(FTP_HOST, FTP_PORT <= 0 ? 21 : FTP_PORT);
            client.login(USERNAME, PASSWORD);
            client.setType(FTPClient.TYPE_BINARY);
            //   client.changeDirectory("/upload/");

            client.upload(path, listener);

        } catch (Exception e) {
            e.printStackTrace();

            try {
                client.disconnect(true);
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }

    }

    public static void ftpUploadFiles(List<File> files, String remoteFolder, IMonitor monitor) throws Exception {

        if ("SFTP".equalsIgnoreCase(FILE_TRANSFER_PROTOCOL)) {
            SFTP.uploadFiles(FTP_HOST_NAME, FTP_PORT <= 0 ? 22 : FTP_PORT, FTP_USER_NAME, FTP_PASSWORD, remoteFolder, files, monitor);
            return;
        }

        FTPClient client = new FTPClient();

        String username = FTP_USER_NAME;
        String password = FTP_PASSWORD;
        String ftpHost = FTP_HOST_NAME;

        try {
            client.connect(ftpHost, FTP_PORT <= 0 ? 21 : FTP_PORT);
            client.login(username, password);
            client.setType(FTPClient.TYPE_BINARY);
            ensureFtpDirectory(client, remoteFolder);

            long totalBytes = 0;
            int totalFiles = 0;
            for (File file : files) {
                if (file != null && file.exists() && file.isFile()) {
                    totalBytes += Math.max(1L, file.length());
                    totalFiles += 1;
                }
            }

            final long totalBytesFinal = totalBytes;
            long uploadedBytes = 0;
            int uploadedFiles = 0;
            for (File file : files) {
                if (file == null || !file.exists() || !file.isFile()) {
                    continue;
                }
                uploadedFiles += 1;
                long fileSize = Math.max(1L, file.length());
                if (monitor != null) {
                    monitor.setNote("Uploading file " + uploadedFiles + " of " + totalFiles + ": " + file.getName());
                }
                final long baseBytes = uploadedBytes;
                client.upload(file, new FTPDataTransferListener() {
                    private long fileTransferred;

                    @Override
                    public void started() {
                    }

                    @Override
                    public void transferred(int length) {
                        fileTransferred += length;
                        if (monitor != null && totalBytesFinal > 0) {
                            long aggregate = Math.min(totalBytesFinal, baseBytes + fileTransferred);
                            int progress = (int) Math.min(100L, (aggregate * 100L) / totalBytesFinal);
                            monitor.setProgress(progress);
                        }
                    }

                    @Override
                    public void completed() {
                        if (monitor != null && totalBytesFinal > 0) {
                            long aggregate = Math.min(totalBytesFinal, baseBytes + fileSize);
                            int progress = (int) Math.min(100L, (aggregate * 100L) / totalBytesFinal);
                            monitor.setProgress(progress);
                        }
                    }

                    @Override
                    public void aborted() {
                    }

                    @Override
                    public void failed() {
                    }
                });
                uploadedBytes += fileSize;
            }
            if (monitor != null) {
                monitor.setProgress(100);
                monitor.setNote("Upload completed.");
                monitor.onEnd();
            }
        } catch (Exception e) {
            try {
                client.disconnect(true);
            } catch (Exception ignored) {
            }
            throw e;
        } finally {
            try {
                client.disconnect(true);
            } catch (Exception ignored) {
            }
        }
    }

    private static void ensureFtpDirectory(FTPClient client, String remoteFolder) throws Exception {
        if (remoteFolder == null || remoteFolder.trim().length() == 0) {
            return;
        }

        String normalized = remoteFolder.trim().replace("\\", "/");
        boolean absolute = normalized.startsWith("/");
        if (absolute) {
            client.changeDirectory("/");
        }

        String[] parts = normalized.split("/");
        for (String part : parts) {
            String segment = part.trim();
            if (segment.length() == 0) {
                continue;
            }
            try {
                client.changeDirectory(segment);
            } catch (Exception changeFailed) {
                client.createDirectory(segment);
                client.changeDirectory(segment);
            }
        }
    }

    public static boolean isJSONValid(String test) {

        if(test==null) {
            return false;
        }

        try {
            new JSONObject(test);
        } catch (JSONException ex) {
            // edited, to include @Arthur's comment
            // e.g. in case JSONArray is valid as well...
            try {
                new JSONArray(test);
            } catch (JSONException ex1) {
                return false;
            }
        }
        return true;
    }

    public static final boolean is64Bit() {

        String model = System.getProperty("sun.arch.data.model",
                System.getProperty("com.ibm.vm.bitmode"));
        if (model != null) {
            return "64".equals(model);
        }

        return false;
    }


    public static int strCode(String s) {

        char[] value = s.toCharArray();
        int h = 0;
        if (value.length > 0) {

            for (int i = 0; i < value.length; i++) {
                h = 31 * h + value[i];
            }

        }
        return h;

    }

    public static String encStrGetEnd(String str) {
        String code = String.valueOf(Math.abs(strCode(str)));
        int len = code.length();
        int middleIndex = len/2;
        String end = code.substring(middleIndex);

        return end;
    }

    public static String encStrGetBegin(String str) {
        String code = String.valueOf(Math.abs(strCode(str)));
        int len = code.length();
        int middleIndex = len/2;
        String begin = code.substring(0,middleIndex);

        return begin;
    }

    public static String encStr(String str) {

        String code = String.valueOf(Math.abs(strCode(str)));
        int len = code.length();
        int middleIndex = len/2;
        String begin = code.substring(0,middleIndex);
        String end = code.substring(middleIndex);

        return begin+"-"+str+"-"+end;
    }

}
