# Zet de ondertekensleutel van deze pc als secret SIGNING_KEYSTORE in de GitHub-repository,
# zodat GitHub Actions updates bouwt met dezelfde sleutel als de geïnstalleerde app.
# De waarde gaat als argument mee: via een pipe verandert Windows PowerShell de tekst.
$keystore = Join-Path $env:USERPROFILE ".android\debug.keystore"
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
& 'C:\Program Files\GitHub CLI\gh.exe' secret set SIGNING_KEYSTORE --repo donders15266/Tint --body $b64
& 'C:\Program Files\GitHub CLI\gh.exe' secret list --repo donders15266/Tint
