# 包租代管 App

以 Android 手機管理不動產、租約與收租，逐步加入押金、修繕、房貸、資金流水及 Google 試算表報表。

## 專案狀態

0.9.0-preview 是整合測試版，已實作手機本地不動產／單位／租約建檔、分層導覽、搜尋篩選、帳單、租金與水電合併開單、每月電表與水電計算、合併匯款分配、部分繳款、收租狀態篩選、繳費明細複製、即時明細更新、千位逗號、帳戶期初、舊收款對應、支出分攤、房貸扣款、共用現金、銀行互轉、對帳、每租約押金收付、部分退款、固定左側欄位名稱、退租分項結算、押金抵付、分次補繳、退租後換新租客及v5完整備份。GitHub Actions 使用固定簽署金鑰產出 APK。

拍照、語音、Google 同步仍未啟用。退租結算需人工核對費用，不自動退款。確認後暫不提供取消結算或更正補繳。尚未完成實機更新及操作驗收，請先使用測試資料。

- [下載及測試說明](docs/apk-preview.md)
- [支出與資金規格及驗收](docs/changes/expenses-cashflow.md)
- [已確認需求](docs/requirements.md)
- [規格驅動開發流程](docs/development-process.md)

## 下一階段

確認本版操作後，加入電表拍照、語音草稿及 Google 唯讀報表同步。每批先定規格，合併相關功能並通過檢查後再提供 APK 測試。

## 原則

App 為帳務來源；Google 試算表規劃為唯讀報表。帳務、照片與備份不提交到 GitHub；簽署密鑰保存於 GitHub Secrets，不寫入程式或 APK。

0.7.0 收款入口改為「收到租金」，日期以日曆點選，月份以年／月選擇，不再手動輸入格式。[規格與驗收](docs/changes/receipt-label-date-picker.md)。

0.7.0 移除「建立年繳租金」入口，水電與租金確認後按月出單；舊預收與年繳紀錄保留。[本批規格與驗收](docs/changes/live-ledger-and-bill-tools.md)。

0.8.0 押金收付入口在租約帳單明細，記帳／報表可看押金總覽。[規格與驗收](docs/changes/deposit-receipts-refunds.md)。

0.9.0 分開保存押金、結算費用與實際退款／補繳。先處理原帳單及預收款，再預覽確認退租。[規格與驗收](docs/changes/lease-termination-settlement.md)。
