# 0.17 Google房東月報手動同步

Google Apps Script服務、進階Sheets API及App同步入口準備完成，尚需使用者在自己帳號建立試算表、貼程式、授權及正式部署。先連線測試（不傳報表），再手動同步選定月份。使用OwnerReport既有數據，不含聯絡方式照片備註銀行帳號。租客雲端權限及背景自動同步不在本批。

私有金鑰由Google端初始化產生，在App以Android Keystore AES-GCM加密保存；不包含在帳務備份或APK。HTTPS正式script.google.com網址嚴格驗證；回應轉址只允許script.googleusercontent.com並只用GET，不再傳金鑰。拒絕登入HTML、錯誤金鑰、非法回應ID。成功記錄月份與台灣時間；失敗保留舊成功時間，不影響手機帳務。

每月3個租務分頁及同步資訊一次batchUpdate。現有同月份受管理分頁清除舊儲存格後覆寫，不追加交易；其他月份及使用者分頁保留。失敗請核對後重試，逾時不保證Google未收到，重試同月份可重複執行。由使用者將試算表共用給爸爸Gmail檢視者。

CI含Node模擬合約測試與Java端網址/金鑰/轉址驗證。真實Google部署、OAuth授權、手機Keystore和網路端到端仍待啟用後實機驗證。啟用詳見google-sync/README.md。
