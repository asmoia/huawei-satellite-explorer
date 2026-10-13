package com.huawei.rsmc;

import android.os.Bundle;

interface IHwSmcCallBack {
    void notifyServiceStatus(in Bundle bundle);
    void notifySysStatus(in Bundle bundle);
    void notifyCaliInfo(in Bundle bundle);
    void notifySateInfo(in Bundle bundle);
    void notifySendStatus(in Bundle bundle);
    void receiveSmcMessage(in Bundle bundle);
}
