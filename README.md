# 包租代管 App

以 Android 手機管理不動產、租約與收租，逐步加入押金、修繕、房貸、資金流水及 Google 試算表報表。

## 專案狀態

0.5.1-preview 是整合測試版，已實作手機本地不動產／單位／租約建檔、分層導覽、搜尋篩選、帳單、租金與水電合併開單、每月電表與水電計算、年繳租金單、合併匯款分配、部分繳款與完整備份。GitHub Actions 使用固定簽署金鑰產出 APK。

支出、銀行餘額、押金收付、退租結算、拍照、語音、Google 同步仍未啟用。尚未完成實機更新及操作驗收，請先使用測試資料。

- [下載及測試說明](docs/apk-preview.md)
- [本次規格與驗收](docs/changes/integrated-rent-batch.md)
- [已確認需求](docs/requirements.md)
- [規格驅動開發流程](docs/development-process.md)

## 下一階段

確認本版操作後，繼續完成支出分攤、房貸、零用金與押金收付，再加入電表拍照、語音草稿及 Google 唯讀報表同步。每批先定規格，合併相關功能並通過檢查後再提供 APK 測試。

## 原則

App 為帳務來源；Google 試算表規劃為唯讀報表。帳務、照片與備份不提交到 GitHub；簽署密鑰保存於 GitHub Secrets，不寫入程式或 APK。
