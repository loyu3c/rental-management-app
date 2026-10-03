# APK 整合測試版

版本 0.5.0-preview，versionCode=5，applicationId=com.loyu3c.rental.preview。固定簽署沿用既有 GitHub Secrets。

## 下載與安裝

登入有權限的 GitHub 帳戶，進入 Actions 的 **Android Fixed Signature APK** 成功執行紀錄，下載 **rental-management-fixed-signature** ZIP，解壓縮後安裝 app-release.apk。Debug Checks 只做檢查，不提供安裝檔。

從同一固定簽署版本更新可使用 Android 覆蓋安裝流程；實際保留資料升級仍待使用者驗證。舊 debug 簽署版若提示簽署不符，請先匯出完整備份，不能直接承諾跨簽署覆蓋；不要在未備份時移除已存資料的版本。

## 一次測試的流程

1. 先在報表匯出完整備份，再安裝；第一次測試請用測試資料。
2. 不動產清單選一棟 → 搜尋／篩選／排序單位 → 單位明細與租約，確認不再整頁堆操作按鈕。
3. 選一個測試租約，建立單月租金帳單，再建立同月水電單（先在租約填月水費與電價）。首末月需手動填實際租金，不自動按天折算。
4. 例如租金10000、水250、電表100→160、電價5，登錄10300合併匯款，預覽後確認，應剩水電250未付。
5. 另建年繳測試租約，開12張每月租金單，登錄一筆全年匯款，核對僅一筆實際收款。
6. 報表匯出 v2 備份，確認租約、帳單、收款都包含。還原會替換全部資料；舊 v1 備份沒有帳務紀錄，還原後帳務清空。

Google、拍照、語音、支出與押金收付尚未啟用。帳單已收款後不能直接撤銷；錯誤收款可撤銷原登錄及分配，但不能當作實際退款。

建置：JDK17、Gradle8.9、AGP8.7.3、compileSdk35。固定簽署流程執行 :app:testReleaseUnitTest :app:assembleRelease :app:lintRelease。CI 成功不代表已完成實機驗證。
