# Windows alternative to fetch_material_symbols.py (no Python needed).
# Run from the project root in PowerShell:   powershell -ExecutionPolicy Bypass -File app\tools\fetch_material_symbols.ps1
$base = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android"
$out  = Join-Path $PSScriptRoot "..\src\main\res\drawable"
New-Item -ItemType Directory -Force -Path $out | Out-Null
$outlined = "arrow_back arrow_forward close check add search visibility visibility_off lock mail person person_add group groups menu_book calendar_month receipt_long payments event_available event_busy event_repeat fact_check settings home school call edit delete more_vert chevron_right expand_more expand_less filter_list info warning error check_circle schedule history language palette brightness_6 dark_mode light_mode fingerprint pin key logout backup download upload share picture_as_pdf notifications account_circle badge undo content_copy help translate paid account_balance_wallet request_quote trending_up savings photo_camera image send cloud_off person_off search_off inbox location_on tune sort today add_circle cancel block lock_reset shield delete_forever timer folder contacts book bolt" -split " "
$filled   = "home groups settings event_available receipt_long fact_check payments group dashboard check_circle error warning cancel" -split " "
$ok = 0; $fail = 0
foreach ($n in $outlined) {
  try { Invoke-WebRequest "$base/$n/materialsymbolsoutlined/${n}_24px.xml" -OutFile (Join-Path $out "ms_$n.xml") -UseBasicParsing; $ok++ } catch { Write-Host "FAILED $n"; $fail++ }
}
foreach ($n in $filled) {
  try { Invoke-WebRequest "$base/$n/materialsymbolsoutlined/${n}_fill1_24px.xml" -OutFile (Join-Path $out "ms_${n}_fill.xml") -UseBasicParsing; $ok++ } catch { Write-Host "FAILED ${n}_fill"; $fail++ }
}
Write-Host "Downloaded $ok icons, $fail failed -> $out"