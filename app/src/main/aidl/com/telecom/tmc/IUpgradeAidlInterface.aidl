// IUpgradeAidlInterface.aidl
package com.telecom.tmc;

import com.telecom.tmc.IUpgradeCallback;
interface IUpgradeAidlInterface {
    oneway void checkUpgrade(String jsonStr, IUpgradeCallback callback);
    String getRomUpdateInfo();
}
