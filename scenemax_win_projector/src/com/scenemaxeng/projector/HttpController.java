package com.scenemaxeng.projector;

import com.scenemaxeng.compiler.*;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

public class HttpController extends CompositeController {

    Thread httpThread = null;
    private HttpGet httpRequest;

    public HttpController(SceneMaxApp app, ProgramDef prg, SceneMaxScope scope, HttpCommand cmd) {
        super(app,prg,scope,cmd);
    }

    public boolean run(float tpf) {

        if (forceStop) return true;
        if (httpThread==null) {
            HttpCommand cmd = (HttpCommand) this.cmd;
            if(cmd.verb==HttpCommand.VERB_TYPE_GET) {
                httpRequest = new HttpGet(cmd, this.scope);
                httpThread = new Thread(httpRequest);
                httpThread.start();
            }

            if(httpThread==null) {
                return true;
            }

        }

        // wait for http thread to finish
        if(httpThread.isAlive()) {
            return false;
        }

        if(this.size()==0) {

            // run http callback

            HttpCommand cmd = (HttpCommand) this.cmd;

            FunctionInvocationCommand fic = new FunctionInvocationCommand();
            fic.funcName = cmd.callbackProcName;

            HashMap<String, Object> params = new HashMap<>();

            VariableDef vd = new VariableDef();
            vd.varType=VariableDef.VAR_TYPE_NUMBER;
            VarInst vi = new VarInst(vd,scope);
            vi.varType=VariableDef.VAR_TYPE_NUMBER;
            vi.value=httpRequest.getResponseCode();
            params.put("code",vi);

            vd = new VariableDef();
            vd.varType=VariableDef.VAR_TYPE_STRING;
            vi = new VarInst(vd,scope);
            vi.varType=VariableDef.VAR_TYPE_STRING;
            vi.value=httpRequest.getResponse();
            params.put("response",vi);

            FunctionBlockDef fDef = prg.getFunc(fic.funcName);
            if (fDef != null) {
                DoBlockCommand cmdDo = fDef.doBlock;
                DoBlockController c = new DoBlockController(this.app, scope, cmdDo);
                c.app = this.app;
                c.goExpr = fDef.goExpr;
                c.setFuncScopeParams(params);
                c.async = fic.isAsync || cmdDo.isAsync;
                this.add(c);
                return false;
            }

            return true; // no procedure found , abort
        }


        // run until http callback procedure is finished
        return super.run(tpf);

    }

    static class HttpGet implements Runnable {

        private final HttpCommand cmd;
        private final SceneMaxScope scope;
        private String response = null;
        private int responseCode;

        public HttpGet(HttpCommand cmd, SceneMaxScope scope) {
            this.cmd=cmd;
            this.scope=scope;
        }

        public void run() {
            String addr = new ActionLogicalExpression(cmd.addressExp,this.scope).evaluate().toString();

            HttpURLConnection connection = null;

            try {
                URL url = new URL(addr);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                responseCode = connection.getResponseCode();

                //connection.setRequestProperty("Authorization", headerForAuthorizeAccount);
                InputStream in = new BufferedInputStream(connection.getInputStream());
                response = myInputStreamReader(in);

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                connection.disconnect();
            }
        }

        public int getResponseCode() {
            return responseCode;
        }

        public String getResponse() {
            return  response;
        }

    }

    static public String myInputStreamReader(InputStream in) throws IOException {
        InputStreamReader reader = new InputStreamReader(in);
        StringBuilder sb = new StringBuilder();
        int c = reader.read();
        while (c != -1) {
            sb.append((char)c);
            c = reader.read();
        }
        reader.close();
        return sb.toString();
    }
}
