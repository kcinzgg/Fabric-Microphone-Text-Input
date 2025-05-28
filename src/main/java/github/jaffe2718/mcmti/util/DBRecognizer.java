package github.jaffe2718.mcmti.util;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import okhttp3.*;
import okhttp3.logging.HttpLoggingInterceptor;
import okio.ByteString;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.concurrent.CountDownLatch;

/**
 * 豆包语音识别
 */
public class DBRecognizer {
    final static String url = "wss://openspeech.bytedance.com/api/v3/sauc/bigmodel_nostream";
    final static String appId = "9954471235";
    final static String token = "riXplQ9eQMUg1K1GJIqgt_kRSWZU3zW9";

    private static final byte PROTOCOL_VERSION = 0b0001;
    private static final byte DEFAULT_HEADER_SIZE = 0b0001;
    // Message Type:
    private static final byte FULL_CLIENT_REQUEST = 0b0001;
    private static final byte AUDIO_ONLY_REQUEST = 0b0010;
    private static final byte FULL_SERVER_RESPONSE = 0b1001;
    private static final byte SERVER_ACK = 0b1011;
    private static final byte SERVER_ERROR_RESPONSE = 0b1111;
    // Message Type Specific Flags
    private static final byte NO_SEQUENCE = 0b0000;// no check sequence
    private static final byte POS_SEQUENCE = 0b0001;
    private static final byte NEG_SEQUENCE = 0b0010;
    private static final byte NEG_WITH_SEQUENCE = 0b0011;
    private static final byte NEG_SEQUENCE_1 = 0b0011;
    // Message Serialization
    private static final byte NO_SERIALIZATION = 0b0000;
    private static final byte JSON = 0b0001;

    // Message Compression
    private static final byte NO_COMPRESSION = 0b0000;
    private static final byte GZIP = 0b0001;

    /**
     * 存储最后一次的识别结果
     */
    private static volatile RecognitionResult lastRecognitionResult = new RecognitionResult();

    /**
     * WebSocket连接池
     */
    private static final OkHttpClient okHttpClient;

    static {
        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.HEADERS);
        okHttpClient = new OkHttpClient.Builder()
                .pingInterval(30, TimeUnit.SECONDS)  // 保持连接活跃
                .addInterceptor(loggingInterceptor)
                .readTimeout(100, TimeUnit.SECONDS)
                .writeTimeout(100, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 语音识别结果类
     */
    public static class RecognitionResult {
        /**
         * 音频时长（毫秒）
         */
        public int duration;
        /**
         * 识别文本
         */
        public String text;
        /**
         * 语音片段列表
         */
        public List<Utterance> utterances;
        /**
         * 识别是否成功
         */
        public boolean isSuccessful;

        public RecognitionResult() {
            this.duration = 0;
            this.text = "";
            this.utterances = new ArrayList<>();
            this.isSuccessful = false;
        }
    }

    /**
     * 执行语音识别
     *
     * @param ins 音频输入流
     * @return 识别结果文本
     * @throws IllegalArgumentException 如果输入流为空
     * @throws RuntimeException         如果识别过程发生错误
     */
    public static String recognize(AudioInputStream ins) {
        if (ins == null) {
            throw new IllegalArgumentException("AudioInputStream cannot be null");
        }

        // 重置结果
        lastRecognitionResult.duration = 0;
        lastRecognitionResult.text = "";
        lastRecognitionResult.utterances.clear();
        lastRecognitionResult.isSuccessful = false;

        AudioFormat format = ins.getFormat();
        WebSocket webSocket = null;
        CountDownLatch latch = new CountDownLatch(1);
        RuntimeException[] error = new RuntimeException[1];
        
        try {
            final Request request = new Request.Builder()
                    .url(url)
                    .header("X-Api-App-Key", appId)
                    .header("X-Api-Access-Key", token)
                    .header("X-Api-Resource-Id", "volc.bigasr.sauc.duration")
                    .build();

            webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
                byte[] buffer;
                int bufferSize;
                int seq;

                @Override
                public void onOpen(WebSocket webSocket, Response response) {
                    String logId = response.header("X-Tt-Logid");
                    System.out.println("===> onOpen,X-Tt-Logid:" + logId);

                    // send full client request
                    // step 1: append payload json string
                    JsonObject user = new JsonObject();
                    user.addProperty("uid", "test");

                    JsonObject audio = new JsonObject();
                    audio.addProperty("format", "pcm"); //
                    audio.addProperty("sample_rate", (int) format.getSampleRate());
                    audio.addProperty("bits", format.getSampleSizeInBits());
                    audio.addProperty("channel", format.getChannels());
                    audio.addProperty("codec", "raw");

                    JsonObject request = new JsonObject();
                    request.addProperty("model_name", "bigmodel");
                    request.addProperty("enable_punc", true);

                    JsonObject payload = new JsonObject();
                    payload.add("user", user);
                    payload.add("audio", audio);
                    payload.add("request", request);

                    String payloadStr = payload.toString();
                    System.out.println(payloadStr);
                    // step2: 压缩 payload 字段。
                    final byte[] payloadBytes = gzipCompress(payloadStr.getBytes());
                    // step3:组装 fullClientRequest；fullClientRequest= header+ sequence + payload
                    byte[] header = getHeader(FULL_CLIENT_REQUEST, POS_SEQUENCE, JSON, GZIP, (byte) 0);
                    final byte[] payloadSize = intToBytes(payloadBytes.length);
                    seq = 1;
                    byte[] seqBytes = generateBeforPayload(seq);
                    final byte[] fullClientRequest = new byte[header.length + seqBytes.length + payloadSize.length
                            + payloadBytes.length];
                    int destPos = 0;
                    System.arraycopy(header, 0, fullClientRequest, destPos, header.length);
                    destPos += header.length;
                    System.arraycopy(seqBytes, 0, fullClientRequest, destPos, seqBytes.length);
                    destPos += seqBytes.length;
                    System.arraycopy(payloadSize, 0, fullClientRequest, destPos, payloadSize.length);
                    destPos += payloadSize.length;
                    System.arraycopy(payloadBytes, 0, fullClientRequest, destPos, payloadBytes.length);
                    boolean suc = webSocket.send(ByteString.of(fullClientRequest));
                    if (!suc) {
                        return;
                    }
                    AudioFormat format = ins.getFormat();
                    // 一次性传输的帧数可视内存及网络承载能力决定，不唯一。
                    int frames = (int) Math.min(ins.getFrameLength(), ins.getFrameLength() / 10);// 切成10 段。
                    bufferSize = (format.getSampleSizeInBits() / Byte.SIZE) * format.getChannels() * frames;
                    buffer = new byte[bufferSize];
                }

                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    super.onMessage(webSocket, text);
                    System.out.println("===> onMessage： text:" + text);
                    try {
                        parseResponse(text, lastRecognitionResult);
                    } catch (Exception e) {
                        System.out.println("解析识别结果失败: " + e.getMessage());
                        lastRecognitionResult.isSuccessful = false;
                        error[0] = new RuntimeException("解析识别结果失败: " + e.getMessage());
                        latch.countDown();
                    }
                }

                @Override
                public void onMessage(WebSocket webSocket, ByteString bytes) {
                    byte[] res = bytes.toByteArray();
                    int sequence = parserResponse(res);
                    boolean is_last_package = sequence < 0;
                    if (is_last_package) {
                        System.out.println("===>退出程序");
                        webSocket.close(1000, "finished");
                        latch.countDown();
                        return;
                    }
                    // send audio only request
                    try {
                        final int len = ins.read(buffer, 0, bufferSize);
                        if (len <= 0) {
                            System.out.println("===>read len <= 0,exit");
                            latch.countDown();
                            return;
                        }
                        boolean isLast = ins.available() == 0;
                        System.out.println("===> read end:" + isLast + " available:" + ins.available());
                        sendAudioOnlyRequest(webSocket, buffer, len, isLast);
                        if (isLast) {
                            ins.close();
                        }
                    } catch (IOException e) {
                        error[0] = new RuntimeException("读取音频数据失败: " + e.getMessage());
                        latch.countDown();
                    }
                }

                // audio_only_request= header + sequence + payload size+ payload
                boolean sendAudioOnlyRequest(WebSocket webSocket, byte[] buffer, int len, boolean isLast) {
                    seq++;
                    System.out.println("seq:" + seq);
                    if (isLast) {
                        seq = -seq;
                    }
                    byte messageTypeSpecificFlags = isLast ? NEG_WITH_SEQUENCE : POS_SEQUENCE;
                    // header
                    byte[] header = getHeader(AUDIO_ONLY_REQUEST, messageTypeSpecificFlags, JSON, GZIP, (byte) 0);
                    // sequence
                    byte[] sequenceBytes = generateBeforPayload(seq);
                    // payload size
                    byte[] payloadBytes = gzipCompress(buffer, len);
                    // payload
                    byte[] payloadSize = intToBytes(payloadBytes.length);
                    byte[] audio_only_request = new byte[header.length + sequenceBytes.length + payloadSize.length
                            + payloadBytes.length];
                    int destPos = 0;
                    System.arraycopy(header, 0, audio_only_request, destPos, header.length);
                    destPos += header.length;
                    System.arraycopy(sequenceBytes, 0, audio_only_request, destPos, sequenceBytes.length);
                    destPos += sequenceBytes.length;
                    System.arraycopy(payloadSize, 0, audio_only_request, destPos, payloadSize.length);
                    destPos += payloadSize.length;
                    System.arraycopy(payloadBytes, 0, audio_only_request, destPos, payloadBytes.length);
                    return webSocket.send(ByteString.of(audio_only_request));
                }

                @Override
                public void onClosing(WebSocket webSocket, int code, String reason) {
                    super.onClosing(webSocket, code, reason);
                    System.out.println("===> onClosing： code:" + code + " reason:" + reason);
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    super.onClosed(webSocket, code, reason);
                    System.out.println("===> onClosed： code:" + code + " reason:" + reason);
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    super.onFailure(webSocket, t, response);
                    String errorMsg = "===> onFailure： Throwable:" + t.getMessage() + " Response:" + (response == null ? "null" : response.toString());
                    System.out.println(errorMsg);
                    error[0] = new RuntimeException(errorMsg);
                    latch.countDown();
                }
            });

            // 等待识别完成或超时
            if (!latch.await(100, TimeUnit.SECONDS)) {
                throw new RuntimeException("识别超时");
            }

            // 检查是否有错误发生
            if (error[0] != null) {
                throw error[0];
            }

            System.out.println("recognizer result:" + lastRecognitionResult.text);
            return lastRecognitionResult.text;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("识别过程被中断", e);
        } finally {
            if (webSocket != null) {
                webSocket.close(1000, "finished");
            }
        }
    }

    static byte[] getHeader(byte messageType, byte messageTypeSpecificFlags, byte serialMethod, byte compressionType,
                            byte reservedData) {
        final byte[] header = new byte[4];
        header[0] = (PROTOCOL_VERSION << 4) | DEFAULT_HEADER_SIZE; // Protocol version|header size
        header[1] = (byte) ((messageType << 4) | messageTypeSpecificFlags); // message type | messageTypeSpecificFlags
        header[2] = (byte) ((serialMethod << 4) | compressionType);
        header[3] = reservedData;
        return header;
    }

    static byte[] intToBytes(int a) {
        return new byte[]{
                (byte) ((a >> 24) & 0xFF),
                (byte) ((a >> 16) & 0xFF),
                (byte) ((a >> 8) & 0xFF),
                (byte) (a & 0xFF)
        };
    }

    static int bytesToInt(byte[] src) {
        if (src == null || (src.length != 4)) {
            throw new IllegalArgumentException("");
        }
        return ((src[0] & 0xFF) << 24)
                | ((src[1] & 0xff) << 16)
                | ((src[2] & 0xff) << 8)
                | ((src[3] & 0xff));
    }

    static byte[] generateBeforPayload(int seq) {
        return intToBytes(seq);
    }

    static byte[] gzipCompress(byte[] src) {
        return gzipCompress(src, src.length);
    }

    static byte[] gzipCompress(byte[] src, int len) {
        if (src == null || len == 0) {
            return new byte[0];
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip = null;
        try {
            gzip = new GZIPOutputStream(out);
            gzip.write(src, 0, len);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (gzip != null) {
                try {
                    gzip.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return out.toByteArray();
    }

    static byte[] gzipDecompress(byte[] src) {
        if (src == null || src.length == 0) {
            return null;
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream ins = new ByteArrayInputStream(src);
        GZIPInputStream gzip = null;
        try {
            gzip = new GZIPInputStream(ins);
            byte[] buffer = new byte[ins.available()];
            int len = 0;
            while ((len = gzip.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (gzip != null) {
                try {
                    gzip.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return out.toByteArray();
    }

    static int parserResponse(byte[] res) {
        if (res == null || res.length == 0) {
            return -1;
        }
        // 当符号位为1时进行 >> 运算后高位补1（预期是补0），导致结果错误，所以增加个数再与其& 运算，目的是确保高位是补0.
        final byte num = 0b00001111;
        Map<String, Object> result = new HashMap<>();
        // header 32 bit=4 byte
        int protocol_version = (res[0] >> 4) & num;
        result.put("protocol_version", protocol_version);
        int header_size = res[0] & 0x0f;
        result.put("header_size", header_size);

        int message_type = (res[1] >> 4) & num;
        result.put("message_type", message_type);
        int message_type_specific_flags = res[1] & 0x0f;
        result.put("message_type_specific_flags", message_type_specific_flags);
        int serialization_method = res[2] >> num;
        result.put("serialization_method", serialization_method);
        int message_compression = res[2] & 0x0f;
        result.put("message_compression", message_compression);
        int reserved = res[3];
        result.put("reserved", reserved);

        // sequence 4 byte
        byte[] temp = new byte[4];
        System.arraycopy(res, 4, temp, 0, temp.length);
        int sequence = bytesToInt(temp);// sequence 4 byte

        // payload size 4 byte
        String payloadStr = null;
        System.arraycopy(res, 8, temp, 0, temp.length);
        int payloadSize = bytesToInt(temp);
        byte[] payload = new byte[res.length - 12];
        System.arraycopy(res, 12, payload, 0, payload.length);
        // 正常Response
        if (message_type == FULL_SERVER_RESPONSE) {
            if (message_compression == GZIP) {
                payloadStr = new String(gzipDecompress(payload));
            } else {
                payloadStr = new String(payload);
            }
            System.out.println("===>FULL_SERVER_RESPONSE:payload:" + payloadStr);
            result.put("payload_size", payloadSize);
            System.out.println("===>FULL_SERVER_RESPONSE:response:" + new Gson().toJson(result));
            // 解析并保存临时结果
            parseResponse(payloadStr, lastRecognitionResult);
        } else if (message_type == SERVER_ACK) {
            payloadStr = new String(payload);
            System.out.println("===>SERVER_ACK:payload:" + payloadStr);
            result.put("payload_size", payloadSize);
            System.out.println("===>SERVER_ACK:response:" + new Gson().toJson(result));
        } else if (message_type == SERVER_ERROR_RESPONSE) {
            // 此时 sequence 含义就是 错误码 code，payload 就是 error msg。
            payloadStr = new String(payload);
            result.put("code", sequence);
            result.put("error msg", payloadStr);
            System.out.println("===>SERVER_ERROR_RESPONSE:response:" + new Gson().toJson(result));
        }
        return sequence;
    }

    /**
     * 解析语音识别响应
     *
     * @param response 服务器返回的JSON响应
     * @param result   要更新的结果对象
     */
    private static void parseResponse(String response, RecognitionResult result) {
        try {
            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();

            // 解析音频信息
            if (jsonObject.has("audio_info")) {
                JsonObject audioInfo = jsonObject.getAsJsonObject("audio_info");
                if (audioInfo.has("duration")) {
                    result.duration = audioInfo.get("duration").getAsInt();
                }
            }

            // 解析识别结果
            if (jsonObject.has("result")) {
                JsonObject resultObj = jsonObject.getAsJsonObject("result");
                if (resultObj.has("text")) {
                    result.text = resultObj.get("text").getAsString();
                }

                // 解析utterances
                if (resultObj.has("utterances")) {
                    result.utterances.clear();
                    JsonArray utterancesArray = resultObj.getAsJsonArray("utterances");
                    for (JsonElement utteranceElement : utterancesArray) {
                        result.utterances.add(parseUtterance(utteranceElement.getAsJsonObject()));
                    }
                }
            }

            result.isSuccessful = true;
        } catch (Exception e) {
            System.out.println("解析识别结果失败: " + e.getMessage());
            result.isSuccessful = false;
            throw new RuntimeException("解析识别结果失败", e);
        }
    }

    /**
     * 语音片段类
     */
    public static class Utterance {
        /**
         * 片段文本
         */
        public String text;
        /**
         * 开始时间（毫秒）
         */
        public int startTime;
        /**
         * 结束时间（毫秒）
         */
        public int endTime;
        /**
         * 是否确定
         */
        public boolean definite;
        /**
         * 单词列表
         */
        public List<Word> words;

        public Utterance() {
            this.text = "";
            this.startTime = 0;
            this.endTime = 0;
            this.definite = false;
            this.words = new ArrayList<>();
        }
    }

    /**
     * 单词类
     */
    public static class Word {
        /**
         * 单词文本
         */
        public String text;
        /**
         * 开始时间（毫秒）
         */
        public int startTime;
        /**
         * 结束时间（毫秒）
         */
        public int endTime;

        public Word() {
            this.text = "";
            this.startTime = 0;
            this.endTime = 0;
        }
    }

    /**
     * 解析语音片段
     *
     * @param jsonObject JSON对象
     * @return 解析后的语音片段对象
     */
    private static Utterance parseUtterance(JsonObject jsonObject) {
        Utterance utterance = new Utterance();

        if (jsonObject.has("text")) {
            utterance.text = jsonObject.get("text").getAsString();
        }
        if (jsonObject.has("start_time")) {
            utterance.startTime = jsonObject.get("start_time").getAsInt();
        }
        if (jsonObject.has("end_time")) {
            utterance.endTime = jsonObject.get("end_time").getAsInt();
        }
        if (jsonObject.has("definite")) {
            utterance.definite = jsonObject.get("definite").getAsBoolean();
        }
        if (jsonObject.has("words")) {
            JsonArray wordsArray = jsonObject.getAsJsonArray("words");
            for (JsonElement wordElement : wordsArray) {
                JsonObject wordObj = wordElement.getAsJsonObject();
                Word word = new Word();
                if (wordObj.has("text")) {
                    word.text = wordObj.get("text").getAsString();
                }
                if (wordObj.has("start_time")) {
                    word.startTime = wordObj.get("start_time").getAsInt();
                }
                if (wordObj.has("end_time")) {
                    word.endTime = wordObj.get("end_time").getAsInt();
                }
                utterance.words.add(word);
            }
        }

        return utterance;
    }
}
