# APK 介面預覽

版本0.1.0-preview。僅供查看五個導覽畫面，尚無正式帳務、拍照、語音或Google同步。

建置：JDK17、Gradle8.9、Android Gradle Plugin8.7.3、compileSdk35。使用gradle :app:assembleDebug :app:lintDebug。

GitHub Actions成功後下載 rental-management-preview ZIP，解壓縮後安裝APK。預覽使用独立applicationId，不承諾與正式版共用資料；此階段不得記正式帳。

目前為debug簽署，尚未設定固定正式簽署金鑰，跨次建置可能需要移除舊預覽版才能安裝。正式版本會另設固定簽署並驗證保留資料更新。
