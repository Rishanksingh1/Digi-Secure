$ErrorActionPreference = "Stop"

function Read-SecureText {
    param([string]$Prompt)

    $inputMode = Read-Host -Prompt "$Prompt [Enter = paste clipboard, M = hidden manual entry]"
    if ([string]::IsNullOrEmpty($inputMode)) {
        try {
            $clipboardValue = Get-Clipboard -Raw -ErrorAction Stop
        }
        catch {
            throw "Could not read the clipboard. Copy the value again or choose M for hidden manual entry."
        }

        if ([string]::IsNullOrWhiteSpace($clipboardValue)) {
            throw "The clipboard is empty. Copy the value again or choose M for hidden manual entry."
        }
        return $clipboardValue
    }

    if ($inputMode -ine "M") {
        throw "Choose Enter to paste from the clipboard or M for hidden manual entry."
    }

    $secureValue = Read-Host -Prompt $Prompt -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        $secureValue.Dispose()
    }
}

function Set-UserAndProcessVariable {
    param(
        [string]$Name,
        [string]$Value
    )

    [Environment]::SetEnvironmentVariable($Name, $Value, "User")
    Set-Item -Path "Env:$Name" -Value $Value
}

Write-Output "This configures Gmail SMTP for Digi Secure using Windows user environment variables."
Write-Output "You can paste the app password from the clipboard without displaying it, or enter it manually at a hidden prompt."
Write-Output "Windows user environment variables are not encrypted; use this only on your trusted Windows account."
Write-Output ""

$email = Read-Host -Prompt "Gmail address to send mail from"
if ([string]::IsNullOrWhiteSpace($email) -or
    $email -notmatch '^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$') {
    throw "Enter a valid Gmail or Google Workspace email address."
}

Write-Output "Gmail SMTP requires 2-Step Verification and an App Password."
Write-Output "Create one at https://myaccount.google.com/apppasswords, then copy its 16 characters."
$appPassword = Read-SecureText -Prompt "Gmail App Password (input is hidden)"
$normalizedPassword = $appPassword -replace '\s', ''
if ($normalizedPassword -notmatch '^[A-Za-z0-9]{16}$') {
    throw "The Gmail App Password must contain 16 letters or digits. Do not enter your regular Google password."
}

Set-UserAndProcessVariable -Name "SMTP_HOST" -Value "smtp.gmail.com"
Set-UserAndProcessVariable -Name "SMTP_PORT" -Value "587"
Set-UserAndProcessVariable -Name "SMTP_USERNAME" -Value $email.Trim()
Set-UserAndProcessVariable -Name "SMTP_PASSWORD" -Value $normalizedPassword
Set-UserAndProcessVariable -Name "SMTP_AUTH" -Value "true"
Set-UserAndProcessVariable -Name "SMTP_STARTTLS" -Value "true"
Set-UserAndProcessVariable -Name "SMTP_FROM" -Value $email.Trim()

$appPassword = $null
$normalizedPassword = $null
Write-Output ""
Write-Output "Gmail SMTP settings saved for your Windows user."
Write-Output "Fully close and reopen VS Code, restart the application, then reload the email-code share link."
Write-Output "The code is sent to the recipient email address configured on that share link."
