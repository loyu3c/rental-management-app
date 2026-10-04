# Google房東報表同步啟用

這份服務尚未在你的Google帳戶部署。App 0.17提供設定、測試與手動同步；第一版不會在每次記帳後自動上傳。使用自己的Google帳戶建立房東試算表。App是帳務來源，Google報表供唯讀查看，不從試算表回寫App。

## 第一次設定（建議用電腦，手機可試桌面版）

1. 在Google雲端硬碟建立空白Google試算表，命名「暖色帳本－房東報表」。不要公開發布，也不要分享給租客。
2. 從該試算表的「擴充功能 → Apps Script」開啟編輯器，把本資料夾 `Code.gs` 的完整內容貼到Code.gs並保存。
3. Apps Script「專案設定」勾選顯示appsscript.json資訊清單；將本資料夾 `appsscript.json` 內容貼入該檔並保存。此設定啟用Google Sheets進階服務。若使用自訂Cloud專案，還需在該專案啟用Google Sheets API。
4. 函式選單選 `setupRentalSync` → 執行，以你的Google帳戶授權存取試算表。初始化會顯示64位App同步金鑰；只複製到App，不貼到聊天、GitHub或試算表。再次執行會顯示原金鑰，不自動換掉。
5. 「部署 → 新增部署 → 網頁應用程式」；執行身分選「我」，誰可以存取選「任何人」。部署後複製以 `/exec` 結尾的正式網址。這是App寫入服務的存取設定，不會把試算表本身公開；每個POST都必須通過私有金鑰驗證。若你的Workspace帳戶不提供「任何人」，目前這種方式不能直接使用，需換成帳號授權方案。
6. App「報表 → Google同步設定／狀態」填部署網址與App同步金鑰，點「測試連線」。這一步不會上傳帳務。成功後，在爸爸的每月總報表選月份，點「立即同步此月份到Google試算表」。此操作上傳該月全部不動產與租客姓名帳務；不傳電話、Gmail、照片、備註或銀行帳號。
7. Google試算表會出現租務_YYYY-MM_總覽／各不動產／租約明細及租務_同步資訊分頁。核對千位逗點、金額與最後成功時間；同月份再次同步覆寫這些受管理分頁，不追加交易。不要在這些分頁手動記帳，更新時會被App報表覆寫；其他月份和非租務分頁保留。
8. 核對成功後，在試算表「共用」加入爸爸的Gmail，角色選「檢視者」。未做共用時爸爸無法查看。租客的獨立檔案與權限不在第一版，不能把房東報表分享給租客。

## 日常操作

手機新增或更改帳務後，回到月報對受影響的每個月份再同步一次。逾時可能已完成Google更新；核對後重試同月份，不會新增重複工作表。App只在收到符合本次请求的成功回應後更新最後成功時間；失敗不修改手機帳務。

重新安裝或換手機，需重新填同步金鑰；App完整備份不含Google設定。覆蓋升級一般沿用原設定，仍需實機驗證。

## 更換金鑰與更新服务

金鑰外洩時，在Apps Script專案設定的指令碼屬性刪除SYNC_TOKEN，再執行setupRentalSync產生新金鑰，並更新App。服務程式改版後，從「管理部署 → 編輯 → 新版本」更新原部署。

## 開發驗證

`node google-sync/tests/run.cjs`：驗證拒絕錯誤金鑰、check不寫入、重傳同月覆寫、單次批次更新、金額格式、公式安全文字、不傳聯絡欄位及非法資料拒絕。這是模擬Google API測試，不等於真實部署測試。

參考Google官方文件：
- https://developers.google.com/apps-script/guides/web
- https://developers.google.com/apps-script/guides/content
- https://developers.google.com/sheets/api/reference/rest/v4/spreadsheets/batchUpdate
