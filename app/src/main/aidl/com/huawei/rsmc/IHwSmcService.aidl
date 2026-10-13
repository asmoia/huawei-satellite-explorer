package com.huawei.rsmc;

import android.os.Bundle;
import com.huawei.rsmc.IHwSmcCallBack;
import com.huawei.rsmc.IHwCompressCallBack;

interface IHwSmcService {
    int startSmcService(IHwSmcCallBack callback);
    int stopSmcService(IHwSmcCallBack callback);
    int sendSmcMessage(int type, in Bundle messageData);
    Bundle querySysCapability();
    Bundle queryProductCapability();
    int queryNextAllowedSendTime();
    int queryNextAllowedReceiveTime();
    void notifyOprateStatus(int status);
    void setCompressPreload(boolean preload);
    void setVoiceCompressPreload(boolean preload);
    void figureCompress(String path, IHwCompressCallBack callback);
    void voiceCompress(String path, IHwCompressCallBack callback);
}
