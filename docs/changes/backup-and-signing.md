# 備份與固定簽署

先提供0.4.0過渡測試版的JSON匯出/還原，涵蓋properties、units、leases及備份格式版本。透過Android文件選擇器保存到使用者指定位置；備份含姓名/Gmail，提示妥善保存。

還原先完整解析與驗證ID、關聯、代號唯一、租期與金額；使用者確認後一次替換目錄。驗證失敗不改動現有資料。此版本只備份已實作的建檔資料，未來帳務須擴充格式。

限制：若舊版簽章無法重現，不能直接覆蓋安裝新的備份版。不能要求使用者刪除舊資料來取得備份功能。此情況先保留舊版，需在舊版核對並另外記錄資料後才能轉換，或尋找可行裝置資料移轉方案。

固定簽署以GitHub Actions Secrets四項設定：ANDROID_KEYSTORE_BASE64、ANDROID_KEYSTORE_PASSWORD、ANDROID_KEY_ALIAS、ANDROID_KEY_PASSWORD。簽署檔不得放repo或APK下載產物；私密值不得寫log。

固定簽署workflow手動觸發，缺Secrets明確失敗，不回退隨機debug簽署。保留原applicationId，versionCode遞增。正式升級保留資料仍需實機測試。

驗收：匯出後還原相同UUID與建檔資料；取消不修改；無效/超大備份拒絕；缺secret拒絕正式建置；同固定key兩版可覆蓋且資料保留。
