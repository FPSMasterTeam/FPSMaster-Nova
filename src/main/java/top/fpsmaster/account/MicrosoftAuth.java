package top.fpsmaster.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Minecraft Java Microsoft login, same Azure app and token chain as the FPSMaster launcher.
 *
 * <p>Public client (no secret): authorization-code + PKCE (same as the launcher) then Xbox Live
 * + XSTS + Minecraft Services. Device-code is kept for tests/tools but is rejected by this Azure
 * app unless it is marked as a mobile public client.
 *
 * <p>Client id defaults to the launcher builtin; override with
 * {@code FPSMASTER_MINECRAFT_CLIENT_ID} or {@code MICROSOFT_CLIENT_ID}. Redirect URL defaults to
 * {@code http://localhost:3389/oauth}; override with {@code FPSMASTER_MINECRAFT_REDIRECT_URL}.
 */
public final class MicrosoftAuth {
    /** Same builtin as {@code FPSMaster-Launcher/.../microsoft_auth.rs}. */
    public static final String DEFAULT_CLIENT_ID = "057064c6-d180-43df-b010-834b4571532f";
    public static final String DEFAULT_REDIRECT_URL = "http://localhost:3389/oauth";
    public static final String SCOPE = "XboxLive.signin offline_access openid profile email";

    private static final String AUTHORIZE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBOX_AUTH_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_ENTITLEMENTS_URL =
            "https://api.minecraftservices.com/entitlements/mcstore";
    private static final String MC_PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile";

    private MicrosoftAuth() {
    }

    public static String clientId() {
        String env = firstNonEmpty(
                System.getenv("FPSMASTER_MINECRAFT_CLIENT_ID"),
                System.getenv("MICROSOFT_CLIENT_ID"),
                System.getProperty("fpsmaster.minecraft.clientId"),
                System.getProperty("microsoft.clientId"));
        return env != null ? env : DEFAULT_CLIENT_ID;
    }

    public static String redirectUrl() {
        String env = firstNonEmpty(
                System.getenv("FPSMASTER_MINECRAFT_REDIRECT_URL"),
                System.getProperty("fpsmaster.minecraft.redirectUrl"));
        return env != null ? env : DEFAULT_REDIRECT_URL;
    }

    /**
     * Opens the same PKCE browser flow as the FPSMaster launcher. Bind the redirect listener first,
     * then open {@link BrowserSession#authorizeUrl} and {@link BrowserSession#await}.
     */
    public static BrowserSession beginBrowserLogin() throws IOException, AuthException {
        String redirect = redirectUrl();
        URI uri;
        try {
            uri = URI.create(redirect);
        } catch (RuntimeException exception) {
            throw new AuthException("Invalid Minecraft redirect URL");
        }
        String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80);
        String path = uri.getPath() == null || uri.getPath().isEmpty() ? "/" : uri.getPath();
        String state = randomToken(24);
        String verifier = randomToken(64);
        String challenge = pkceChallenge(verifier);
        ServerSocket socket;
        try {
            socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(host, port));
            socket.setSoTimeout(200);
        } catch (IOException exception) {
            throw new AuthException("无法监听微软登录回调 " + host + ":" + port + "（端口被占用）。"
                    + "请关闭占用该端口的程序，或设置 FPSMASTER_MINECRAFT_REDIRECT_URL。");
        }
        return new BrowserSession(buildAuthorizeUrl(redirect, state, challenge), socket, path, state, verifier, redirect);
    }

    static String buildAuthorizeUrl(String redirect, String state, String challenge) {
        return AUTHORIZE_URL
                + "?client_id=" + encode(clientId())
                + "&response_type=code"
                + "&redirect_uri=" + encode(redirect)
                + "&response_mode=query"
                + "&scope=" + encode(SCOPE)
                + "&state=" + encode(state)
                + "&code_challenge=" + encode(challenge)
                + "&code_challenge_method=S256";
    }

    static String pkceChallenge(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(digest.digest(verifier.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is required for Microsoft PKCE", exception);
        }
    }

    static String randomToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static DeviceLogin startDeviceLogin() throws IOException, AuthException {
        Map<String, String> form = new HashMap<String, String>();
        form.put("client_id", clientId());
        form.put("scope", SCOPE);
        Http.Result response = Http.postForm(DEVICE_CODE_URL, form);
        if (!response.success()) {
            throw new AuthException(parseError(response.body, "Failed to start Microsoft device login"));
        }
        return parseDeviceLogin(response.body);
    }

    public static PollResult pollDeviceLogin(String deviceCode) throws IOException, AuthException {
        if (deviceCode == null || deviceCode.trim().isEmpty()) {
            throw new AuthException("Minecraft device code is empty");
        }
        Map<String, String> form = new HashMap<String, String>();
        form.put("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
        form.put("client_id", clientId());
        form.put("device_code", deviceCode.trim());
        Http.Result response = Http.postForm(TOKEN_URL, form);
        if (response.success()) {
            JsonObject token = asObject(response.body);
            String access = stringField(token, "access_token");
            String refresh = optionalString(token, "refresh_token");
            if (access.isEmpty()) {
                throw new AuthException("Microsoft token response did not include an access token");
            }
            MinecraftProfile account = complete(access, refresh);
            return PollResult.completed(account);
        }
        JsonObject errorJson = tryObject(response.body);
        String errorCode = errorJson == null ? "" : optionalString(errorJson, "error").toLowerCase(Locale.ROOT);
        String description = errorJson == null ? "" : optionalString(errorJson, "error_description");
        String classified = classifyTokenError(errorCode);
        if ("pending".equals(classified)) {
            return PollResult.pending(5, firstNonEmpty(description, "Waiting for Microsoft confirmation."));
        }
        if ("slow_down".equals(classified)) {
            return PollResult.pending(8, firstNonEmpty(description, "Waiting for Microsoft confirmation."));
        }
        if ("denied".equals(classified)) {
            return PollResult.denied(firstNonEmpty(description, "Microsoft device login was cancelled."));
        }
        if ("expired".equals(classified)) {
            return PollResult.expired(firstNonEmpty(description, "Microsoft device login code expired."));
        }
        throw new AuthException(firstNonEmpty(description, parseError(response.body, "Microsoft device login failed")));
    }

    public static MinecraftProfile refresh(String refreshToken) throws IOException, AuthException {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            throw new AuthException("Minecraft refresh token is empty");
        }
        Map<String, String> form = new HashMap<String, String>();
        form.put("grant_type", "refresh_token");
        form.put("client_id", clientId());
        form.put("refresh_token", refreshToken.trim());
        form.put("scope", SCOPE);
        Http.Result response = Http.postForm(TOKEN_URL, form);
        if (!response.success()) {
            throw new AuthException(parseError(response.body,
                    "Microsoft premium account refresh failed. Please sign in again."));
        }
        JsonObject token = asObject(response.body);
        String access = stringField(token, "access_token");
        String nextRefresh = optionalString(token, "refresh_token");
        if (nextRefresh.isEmpty()) {
            nextRefresh = refreshToken.trim();
        }
        if (access.isEmpty()) {
            throw new AuthException("Microsoft refresh response did not include an access token");
        }
        return complete(access, nextRefresh);
    }

    public static MinecraftProfile complete(String microsoftAccessToken, String refreshToken)
            throws IOException, AuthException {
        XboxAuth xbox = authenticateXbox(microsoftAccessToken);
        XboxAuth xsts = authorizeXsts(xbox.token);
        String userHash = firstNonEmpty(xsts.uhs, xbox.uhs);
        if (userHash == null) {
            throw new AuthException("Xbox Live authentication response did not include a user hash");
        }
        MinecraftLogin mc = loginMinecraft(userHash, xsts.token);
        assertMinecraftLicense(mc.accessToken);
        JsonObject profile = fetchProfile(mc.accessToken);
        String name = stringField(profile, "name");
        String uuid = dashedUuid(stringField(profile, "id"));
        if (name.isEmpty() || uuid.isEmpty()) {
            throw new AuthException("Minecraft profile response was incomplete");
        }
        MinecraftProfile result = new MinecraftProfile();
        result.name = name;
        result.uuid = uuid;
        result.accessToken = mc.accessToken;
        result.refreshToken = refreshToken;
        result.xuid = firstNonEmpty(xsts.xuid, xbox.xuid);
        result.expiresAt = System.currentTimeMillis() + mc.expiresIn * 1000L;
        return result;
    }

    public static String dashedUuid(String raw) {
        if (raw == null) {
            return "";
        }
        String hex = raw.replace("-", "").trim().toLowerCase(Locale.ROOT);
        if (hex.length() != 32) {
            return raw.trim();
        }
        return hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-" + hex.substring(12, 16)
                + "-" + hex.substring(16, 20) + "-" + hex.substring(20);
    }

    static DeviceLogin parseDeviceLogin(String body) throws AuthException {
        JsonObject json = asObject(body);
        DeviceLogin login = new DeviceLogin();
        login.deviceCode = stringField(json, "device_code");
        login.userCode = stringField(json, "user_code");
        login.verificationUri = stringField(json, "verification_uri");
        login.verificationUriComplete = optionalString(json, "verification_uri_complete");
        login.expiresIn = intField(json, "expires_in", 900);
        login.interval = Math.max(1, intField(json, "interval", 5));
        login.message = optionalString(json, "message");
        if (login.deviceCode.isEmpty() || login.userCode.isEmpty() || login.verificationUri.isEmpty()) {
            throw new AuthException("Microsoft device login response was incomplete");
        }
        return login;
    }

    static String classifyTokenError(String errorCode) {
        if (errorCode == null) {
            return "failed";
        }
        String code = errorCode.trim().toLowerCase(Locale.ROOT);
        if ("authorization_pending".equals(code)) {
            return "pending";
        }
        if ("slow_down".equals(code)) {
            return "slow_down";
        }
        if ("authorization_declined".equals(code) || "access_denied".equals(code)) {
            return "denied";
        }
        if ("expired_token".equals(code) || "bad_verification_code".equals(code)) {
            return "expired";
        }
        return "failed";
    }

    static boolean hasMinecraftLicense(JsonObject entitlements) {
        if (entitlements == null || !entitlements.has("items") || !entitlements.get("items").isJsonArray()) {
            return false;
        }
        JsonArray items = entitlements.getAsJsonArray("items");
        for (JsonElement element : items) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            String name = optionalString(element.getAsJsonObject(), "name");
            if ("product_minecraft".equalsIgnoreCase(name) || "game_minecraft".equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    private static XboxAuth authenticateXbox(String microsoftAccessToken) throws IOException, AuthException {
        JsonObject properties = new JsonObject();
        properties.addProperty("AuthMethod", "RPS");
        properties.addProperty("SiteName", "user.auth.xboxlive.com");
        properties.addProperty("RpsTicket", "d=" + microsoftAccessToken);
        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "http://auth.xboxlive.com");
        body.addProperty("TokenType", "JWT");
        Http.Result response = Http.postJson(XBOX_AUTH_URL, body);
        if (!response.success()) {
            throw new AuthException(parseError(response.body, "Xbox Live authentication failed"));
        }
        return parseXbox(response.body);
    }

    private static XboxAuth authorizeXsts(String xboxToken) throws IOException, AuthException {
        JsonArray userTokens = new JsonArray();
        userTokens.add(new JsonPrimitive(xboxToken));
        JsonObject properties = new JsonObject();
        properties.addProperty("SandboxId", "RETAIL");
        properties.add("UserTokens", userTokens);
        JsonObject body = new JsonObject();
        body.add("Properties", properties);
        body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
        body.addProperty("TokenType", "JWT");
        Http.Result response = Http.postJson(XSTS_URL, body);
        if (!response.success()) {
            throw new AuthException(parseError(response.body,
                    "Minecraft premium account is not eligible for Xbox authorization"));
        }
        return parseXbox(response.body);
    }

    private static MinecraftLogin loginMinecraft(String userHash, String xstsToken)
            throws IOException, AuthException {
        JsonObject body = new JsonObject();
        body.addProperty("identityToken", "XBL3.0 x=" + userHash + ";" + xstsToken);
        Http.Result response = Http.postJson(MC_LOGIN_URL, body);
        if (!response.success()) {
            throw new AuthException(parseError(response.body, "Minecraft Services login failed"));
        }
        JsonObject json = asObject(response.body);
        MinecraftLogin login = new MinecraftLogin();
        login.accessToken = stringField(json, "access_token");
        login.expiresIn = intField(json, "expires_in", 86400);
        if (login.accessToken.isEmpty()) {
            throw new AuthException("Minecraft Services login response did not include an access token");
        }
        return login;
    }

    private static void assertMinecraftLicense(String mcAccessToken) throws IOException, AuthException {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + mcAccessToken);
        Http.Result response = Http.get(MC_ENTITLEMENTS_URL, headers);
        if (!response.success()) {
            String fallback = response.status == 403
                    ? "Minecraft Services API access was denied. Make sure this Azure app has been granted Minecraft API access."
                    : "Failed to fetch Minecraft entitlements";
            throw new AuthException(parseError(response.body, fallback));
        }
        if (!hasMinecraftLicense(asObject(response.body))) {
            throw new AuthException("NO_JAVA_LICENSE");
        }
    }

    private static JsonObject fetchProfile(String mcAccessToken) throws IOException, AuthException {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Bearer " + mcAccessToken);
        Http.Result response = Http.get(MC_PROFILE_URL, headers);
        if (!response.success()) {
            String fallback = response.status == 404
                    ? "NO_JAVA_PROFILE"
                    : response.status == 403
                    ? "Minecraft Services API access was denied. Make sure this Azure app has been granted Minecraft API access."
                    : "Failed to fetch Minecraft profile";
            throw new AuthException(parseError(response.body, fallback));
        }
        return asObject(response.body);
    }

    private static XboxAuth parseXbox(String body) throws AuthException {
        JsonObject json = asObject(body);
        XboxAuth auth = new XboxAuth();
        auth.token = stringField(json, "Token");
        if (auth.token.isEmpty()) {
            throw new AuthException("Xbox Live authentication response did not include a token");
        }
        if (json.has("DisplayClaims") && json.get("DisplayClaims").isJsonObject()) {
            JsonObject claims = json.getAsJsonObject("DisplayClaims");
            if (claims.has("xui") && claims.get("xui").isJsonArray()) {
                JsonArray users = claims.getAsJsonArray("xui");
                if (users.size() > 0 && users.get(0).isJsonObject()) {
                    JsonObject user = users.get(0).getAsJsonObject();
                    auth.uhs = optionalString(user, "uhs");
                    auth.xuid = optionalString(user, "xid");
                }
            }
        }
        return auth;
    }

    private static JsonObject asObject(String body) throws AuthException {
        JsonObject object = tryObject(body);
        if (object == null) {
            throw new AuthException("Unexpected Microsoft login response");
        }
        return object;
    }

    private static JsonObject tryObject(String body) {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            JsonElement element = new JsonParser().parse(body);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String parseError(String body, String fallback) {
        JsonObject json = tryObject(body);
        if (json == null) {
            return fallback;
        }
        String description = firstNonEmpty(
                optionalString(json, "error_description"),
                optionalString(json, "errorMessage"),
                optionalString(json, "error"),
                optionalString(json, "Message"));
        return description != null ? description : fallback;
    }

    private static String stringField(JsonObject json, String key) {
        return optionalString(json, key);
    }

    private static String optionalString(JsonObject json, String key) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return "";
        }
        try {
            return json.get(key).getAsString().trim();
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    private static int intField(JsonObject json, String key, int fallback) {
        if (json == null || !json.has(key) || json.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return json.get(key).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    public static final class BrowserSession implements AutoCloseable {
        public final String authorizeUrl;
        private final ServerSocket socket;
        private final String expectedPath;
        private final String expectedState;
        private final String verifier;
        private final String redirect;
        private volatile boolean closed;

        BrowserSession(String authorizeUrl, ServerSocket socket, String expectedPath, String expectedState,
                       String verifier, String redirect) {
            this.authorizeUrl = authorizeUrl;
            this.socket = socket;
            this.expectedPath = expectedPath;
            this.expectedState = expectedState;
            this.verifier = verifier;
            this.redirect = redirect;
        }

        public MinecraftProfile await(BooleanSupplier cancelled) throws IOException, AuthException {
            try {
                String code = waitForCode(cancelled);
                if (cancelled != null && cancelled.getAsBoolean()) {
                    throw new AuthException("cancelled");
                }
                return exchangeAuthorizationCode(code, verifier, redirect);
            } finally {
                close();
            }
        }

        private String waitForCode(BooleanSupplier cancelled) throws IOException, AuthException {
            long deadline = System.currentTimeMillis() + 5L * 60L * 1000L;
            while (System.currentTimeMillis() < deadline) {
                if (cancelled != null && cancelled.getAsBoolean()) {
                    throw new AuthException("cancelled");
                }
                Socket client;
                try {
                    client = socket.accept();
                } catch (SocketTimeoutException timeout) {
                    continue;
                } catch (IOException exception) {
                    if (closed || (cancelled != null && cancelled.getAsBoolean())) {
                        throw new AuthException("cancelled");
                    }
                    throw exception;
                }
                try {
                    String requestLine;
                    BufferedReader reader = new BufferedReader(
                            new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
                    requestLine = reader.readLine();
                    while (true) {
                        String header = reader.readLine();
                        if (header == null || header.isEmpty()) {
                            break;
                        }
                    }
                    Map<String, String> query = parseCallback(requestLine);
                    if (query.containsKey("error")) {
                        String message = firstNonEmpty(query.get("error_description"), query.get("error"),
                                "Microsoft login was cancelled.");
                        writeCallbackPage(client, 400, "Microsoft login failed", message);
                        throw new AuthException(message);
                    }
                    String state = query.get("state");
                    if (state == null || !expectedState.equals(state)) {
                        writeCallbackPage(client, 400, "Microsoft login failed",
                                "Microsoft login callback state did not match.");
                        throw new AuthException("Microsoft login callback state did not match.");
                    }
                    String code = query.get("code");
                    if (code == null || code.trim().isEmpty()) {
                        writeCallbackPage(client, 400, "Microsoft login failed",
                                "Microsoft login callback did not contain an authorization code.");
                        throw new AuthException("Microsoft login callback did not contain an authorization code");
                    }
                    writeCallbackPage(client, 200, "Microsoft login completed",
                            "You can return to FPSMaster now.");
                    return code.trim();
                } finally {
                    try {
                        client.close();
                    } catch (IOException ignored) {
                    }
                }
            }
            throw new AuthException("Timed out waiting for Microsoft browser login");
        }

        private Map<String, String> parseCallback(String requestLine) {
            Map<String, String> query = new HashMap<String, String>();
            if (requestLine == null) {
                return query;
            }
            String[] parts = requestLine.split(" ");
            if (parts.length < 2) {
                return query;
            }
            String pathAndQuery = parts[1];
            int q = pathAndQuery.indexOf('?');
            String path = q < 0 ? pathAndQuery : pathAndQuery.substring(0, q);
            if (!path.equals(expectedPath) && !(expectedPath + "/").equals(path) && !path.startsWith(expectedPath)) {
                return query;
            }
            if (q < 0 || q + 1 >= pathAndQuery.length()) {
                return query;
            }
            String[] pairs = pathAndQuery.substring(q + 1).split("&");
            for (int i = 0; i < pairs.length; i++) {
                String pair = pairs[i];
                int eq = pair.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                query.put(decode(pair.substring(0, eq)), decode(pair.substring(eq + 1)));
            }
            return query;
        }

        public void close() {
            closed = true;
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static MinecraftProfile exchangeAuthorizationCode(String code, String verifier, String redirect)
            throws IOException, AuthException {
        Map<String, String> form = new HashMap<String, String>();
        form.put("grant_type", "authorization_code");
        form.put("client_id", clientId());
        form.put("code", code);
        form.put("redirect_uri", redirect);
        form.put("code_verifier", verifier);
        form.put("scope", SCOPE);
        Http.Result response = Http.postForm(TOKEN_URL, form);
        if (!response.success()) {
            throw new AuthException(parseError(response.body, "Microsoft browser login failed"));
        }
        JsonObject token = asObject(response.body);
        String access = stringField(token, "access_token");
        String refresh = optionalString(token, "refresh_token");
        if (access.isEmpty()) {
            throw new AuthException("Microsoft token response did not include an access token");
        }
        return complete(access, refresh);
    }

    private static void writeCallbackPage(Socket socket, int status, String title, String message) {
        try {
            String body = "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>" + title
                    + "</title></head><body style=\"font-family:sans-serif;background:#10161c;color:#eef3f7;"
                    + "display:flex;align-items:center;justify-content:center;min-height:100vh;margin:0;\">"
                    + "<div style=\"max-width:480px;padding:32px;border-radius:20px;background:rgba(255,255,255,0.05);"
                    + "border:1px solid rgba(255,255,255,0.08);\"><h1 style=\"margin:0 0 12px;font-size:22px;\">"
                    + title + "</h1><p style=\"margin:0;font-size:14px;line-height:1.7;color:#c7d2de;\">"
                    + message + "</p></div></body></html>";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            String header = "HTTP/1.1 " + status + " OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: "
                    + bytes.length + "\r\nConnection: close\r\n\r\n";
            OutputStream out = socket.getOutputStream();
            out.write(header.getBytes(StandardCharsets.US_ASCII));
            out.write(bytes);
            out.flush();
        } catch (IOException ignored) {
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (RuntimeException ignored) {
            return value;
        }
    }

    public static final class DeviceLogin {
        public String deviceCode;
        public String userCode;
        public String verificationUri;
        public String verificationUriComplete;
        public int expiresIn;
        public int interval;
        public String message;

        public String browserUrl() {
            return verificationUriComplete != null && !verificationUriComplete.isEmpty()
                    ? verificationUriComplete
                    : verificationUri;
        }
    }

    public static final class PollResult {
        public final String status;
        public final int interval;
        public final String error;
        public final MinecraftProfile account;

        private PollResult(String status, int interval, String error, MinecraftProfile account) {
            this.status = status;
            this.interval = interval;
            this.error = error;
            this.account = account;
        }

        public static PollResult pending(int interval, String message) {
            return new PollResult("pending", interval, message, null);
        }

        public static PollResult completed(MinecraftProfile account) {
            return new PollResult("completed", 0, null, account);
        }

        public static PollResult denied(String message) {
            return new PollResult("denied", 0, message, null);
        }

        public static PollResult expired(String message) {
            return new PollResult("expired", 0, message, null);
        }

        public boolean isPending() {
            return "pending".equals(status) || "slow_down".equals(status);
        }
    }

    public static final class MinecraftProfile {
        public String name;
        public String uuid;
        public String accessToken;
        public String refreshToken;
        public String xuid;
        public long expiresAt;
    }

    private static final class XboxAuth {
        String token;
        String uhs;
        String xuid;
    }

    private static final class MinecraftLogin {
        String accessToken;
        int expiresIn;
    }

    public static final class AuthException extends Exception {
        public AuthException(String message) {
            super(message);
        }
    }

    private static final class Http {
        private static final HttpClient CLIENT = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        static final class Result {
            final int status;
            final String body;

            Result(int status, String body) {
                this.status = status;
                this.body = body == null ? "" : body;
            }

            boolean success() {
                return status >= 200 && status < 300;
            }
        }

        static Result get(String url, Map<String, String> headers) throws IOException {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET();
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    builder.header(header.getKey(), header.getValue());
                }
            }
            return send(builder.build());
        }

        static Result postForm(String url, Map<String, String> form) throws IOException {
            StringBuilder encoded = new StringBuilder();
            if (form != null) {
                for (Map.Entry<String, String> entry : form.entrySet()) {
                    if (encoded.length() > 0) {
                        encoded.append('&');
                    }
                    encoded.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                    encoded.append('=');
                    encoded.append(URLEncoder.encode(entry.getValue() == null ? "" : entry.getValue(),
                            StandardCharsets.UTF_8));
                }
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(encoded.toString()))
                    .build();
            return send(request);
        }

        static Result postJson(String url, JsonObject json) throws IOException {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                    .build();
            return send(request);
        }

        private static Result send(HttpRequest request) throws IOException {
            try {
                HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                return new Result(response.statusCode(), response.body());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException(interrupted);
            }
        }
    }
}
