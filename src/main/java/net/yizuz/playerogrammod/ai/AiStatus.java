package net.yizuz.playerogrammod.ai;

public record AiStatus(
        AiState state,
        String platform,
        String runtimeVersion,
        String runtimePath,
        boolean runtimeInstalled,
        String modelPath,
        boolean modelIsDefault,
        String host,
        int port,
        long pid,
        long uptimeSeconds,
        String lastError
) {
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("state=").append(state);
        sb.append(", platform=").append(platform);
        sb.append(", runtime=").append(runtimeVersion).append(runtimeInstalled ? " (installed)" : " (missing)");
        if (runtimePath != null && !runtimePath.isBlank()) {
            sb.append(" at ").append(runtimePath);
        }
        sb.append(", model=");
        if (modelPath == null || modelPath.isBlank()) {
            sb.append("default (not installed)");
        } else {
            sb.append(modelPath).append(modelIsDefault ? " [default]" : " [custom]");
        }
        sb.append(", endpoint=").append(host).append(':').append(port > 0 ? Integer.toString(port) : "auto");
        if (pid > 0) {
            sb.append(", pid=").append(pid);
        }
        if (uptimeSeconds > 0) {
            sb.append(", uptime=").append(uptimeSeconds).append('s');
        }
        if (lastError != null && !lastError.isBlank()) {
            sb.append(", lastError=").append(lastError);
        }
        return sb.toString();
    }
}
