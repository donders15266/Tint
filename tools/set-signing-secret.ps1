# Zet de ondertekensleutel van deze pc als secret SIGNING_KEYSTORE in de GitHub-repository,
# zodat GitHub Actions updates bouwt met dezelfde sleutel als de geïnstalleerde app.
$keystore = Join-Path $env:USERPROFILE ".android\debug.keystore"
$b64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
$b64 | & 'C:\Program Files\GitHub CLI\gh.exe' secret set SIGNING_KEYSTORE --repo donders15266/Tint
& 'C:\Program Files\GitHub CLI\gh.exe' secret list --repo donders15266/Tint
