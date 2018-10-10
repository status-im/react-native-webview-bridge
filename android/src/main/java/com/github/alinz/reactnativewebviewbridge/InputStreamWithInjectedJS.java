package com.github.alinz.reactnativewebviewbridge;

import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashMap;
import java.util.Map;

public class InputStreamWithInjectedJS extends InputStream {
    private InputStream pageIS;
    private InputStream scriptIS;
    private Charset charset;
    private static final String TAG = "InpStreamWithInjectedJS";
    private static Map<Charset, String> script = new HashMap<>();
    private int GREATER_THAN_SIGN = 62;
    private int LESS_THAN_SIGN = 60;
    private int SCRIPT_TAG_LENGTH = 7;


    private boolean hasJS = false;
    private boolean tagWasFound = false;
    private int[] tag = new int[SCRIPT_TAG_LENGTH];
    private boolean readFromTagVector = false;
    private int tagVectorIdx = 0;
    private int maxTagVectorIdx = SCRIPT_TAG_LENGTH;
    private boolean scriptWasInjected = false;
    private StringBuffer contentBuffer = new StringBuffer();

    private static Charset getCharset(String charsetName) {
        Charset cs = StandardCharsets.UTF_8;
        try {
            if (charsetName != null) {
                cs = Charset.forName(charsetName);
            }
        } catch (UnsupportedCharsetException e) {
            Log.d(TAG, "wrong charset: " + charsetName);
        }

        return cs;
    }

    private static InputStream getScript(Charset charset) {
        String js = script.get(charset);
        if (js == null) {
            String defaultJs = script.get(StandardCharsets.UTF_8);
            js = new String(defaultJs.getBytes(StandardCharsets.UTF_8), charset);
            script.put(charset, js);
        }

        return new ByteArrayInputStream(js.getBytes(charset));
    }

    InputStreamWithInjectedJS(InputStream is, String js, Charset charset) {
        if (js == null) {
            this.pageIS = is;
        } else {
            this.hasJS = true;
            this.charset = charset;
            Charset cs = StandardCharsets.UTF_8;
            String jsScript = "<script>" + js + "</script>";
            script.put(cs, jsScript);
            this.pageIS = is;
        }
    }

    private int readScript() throws IOException {
        int nextByte = scriptIS.read();
        if (nextByte == -1) {
            scriptIS.close();
            scriptWasInjected = true;
            if(readFromTagVector) {
                return readTag();
            } else {
                return pageIS.read();
            }
        } else {
            return nextByte;
        }
    }

    private int readTag() {
        int nextByte = tag[tagVectorIdx];
        tagVectorIdx++;
        if(tagVectorIdx > maxTagVectorIdx) {
            readFromTagVector = false;
        }

        return nextByte;
    }

    private boolean checkHeadTag(int nextByte) {
        int bufferLength = contentBuffer.length();
        if (nextByte == GREATER_THAN_SIGN &&
                bufferLength >= 6 &&
                contentBuffer.substring(bufferLength - 6).equals("<head>")) {

            Log.d(TAG, "<head> tag was found");
            this.scriptIS = getScript(this.charset);
            tagWasFound = true;

            return true;
        }

        return false;
    }

    private boolean checkScriptTagByByte(int index, int anotherByte) {
        if(index == 1) {
            // 115 = "s"
            return anotherByte == 115;
        } else if(index == 2) {
            // 99 = "c"
            return anotherByte == 99;
        }

        return true;
    }

    private boolean checkScriptTag(int nextByte) throws IOException {
        if (nextByte == LESS_THAN_SIGN) {
            StringBuilder tagBuffer = new StringBuilder();
            tag[0] = nextByte;
            tagBuffer.append((char) nextByte);
            readFromTagVector = true;
            tagVectorIdx = 1;
            maxTagVectorIdx = SCRIPT_TAG_LENGTH - 1;
            for (int i = 1; i < SCRIPT_TAG_LENGTH; i++) {
                int anotherByte = pageIS.read();
                tag[i] = anotherByte;
                tagBuffer.append((char) anotherByte);
                contentBuffer.append((char) anotherByte);
                if (!checkScriptTagByByte(i, anotherByte) || anotherByte == -1) {
                    maxTagVectorIdx = i;
                    return false;
                }
            }

            if(tagBuffer.length() == SCRIPT_TAG_LENGTH) {
                String sub = tagBuffer.substring(0, SCRIPT_TAG_LENGTH);
                if (sub.equals("<script")) {
                    tagVectorIdx = 0;
                    Log.d(TAG, "<script tag was found");
                    this.scriptIS = getScript(this.charset);
                    tagWasFound = true;

                    return true;
                } else {
                    return false;
                }
            } else {
                return false;
            }
        } else {
            return false;
        }
    }

    @Override
    public int read() throws IOException {
        if ((scriptWasInjected || !hasJS) && !readFromTagVector) {
            return pageIS.read();
        } else if (!scriptWasInjected && tagWasFound) {
            return readScript();
        } else if (readFromTagVector) {
            return readTag();
        } else {
            int nextByte = pageIS.read();
            contentBuffer.append((char) nextByte);

            if (checkHeadTag(nextByte)) {
                return nextByte;
            } else if (checkScriptTag(nextByte)) {
                return scriptIS.read();
            } else {
                return nextByte;
            }
        }
    }

}
