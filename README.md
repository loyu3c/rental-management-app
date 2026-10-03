# 包租代管 App

以 Android 手機管理不動產、租約與收租，逐步加入押金、修繕、房貸、資金流水及 Google 試算表報表。

## 專案狀態

0.6.1-preview 是整合測試版，已實作手機本地不動產／單位／租約建檔、分層導覽、搜尋篩選、帳單、租金與水電合併開單、每月電表與水電計算、年繳租金單、合併匯款分配、部分繳款、帳戶期初、舊收款對應、支出分攤、房貸扣款、共用現金、銀行互轉、對帳及v3完整備份。GitHub Actions 使用固定簽署金鑰產出 APK。

押金收付、退租結算、拍照、語音、Google 同步仍未啟用。尚未完成實機更新及操作驗收，請先使用測試資料。

- [下載及測試說明](docs/apk-preview.md)
- [支出與資金規格及驗收](docs/changes/expenses-cashflow.md)
- [已確認需求](docs/requirements.md)
- [規格驅動開發流程](docs/development-process.md)

## 下一階段

確認本版操作後，繼續完成押金收付與退租結算，再加入電表拍照、語音草稿及 Google 唯讀報表同步。每批先定規格，合併相關功能並通過檢查後再提供 APK 測試。

## 原則

App 為帳務來源；Google 試算表規劃為唯讀報表。帳務、照片與備份不提交到 GitHub；簽署密鑰保存於 GitHub Secrets，不寫入程式或 APK。

0.6.1 收款入口改為「收到租金」，日期以日曆點選，月份以年／月選擇，不再手動輸入格式。[規格與驗收](docs/changes/receipt-label-date-picker.md)。
