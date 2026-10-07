package cn.rainafter.app.gazekeep.xposed.protocol;

oneway interface IGazeKeepClient {
    void onPreTimeout(long seq, long screenOffAtUptime, long screenDimAtUptime, String topPackage, long lastExternalActivityUptime);
    void onHookStatus(int status);
}
