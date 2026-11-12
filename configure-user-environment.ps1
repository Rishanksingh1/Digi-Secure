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

function Set-RequiredUserVariable {
    param(
        [string]$Name,
        [string]$Prompt,
        [scriptblock]$Validator
    )

    $existingValue = [Environment]::GetEnvironmentVariable($Name, "User")
    if (-not [string]::IsNullOrWhiteSpace($existingValue)) {
        Write-Output "$Name is already configured for your Windows user; keeping the existing value."
        return
    }

    while ($true) {
        $value = Read-SecureText -Prompt $Prompt
        if ([string]::IsNullOrWhiteSpace($value)) {
            Write-Output "A value is required for $Name."
            continue
        }

        if ($Validator -and -not (& $Validator $value)) {
            Write-Output "The value entered for $Name is invalid. Please try again."
            continue
        }

        [Environment]::SetEnvironmentVariable($Name, $value, "User")
        Set-Item -Path "Env:$Name" -Value $value
        Write-Output "$Name saved for your Windows user."
        return
    }
}

Write-Output "This setup stores configuration in your Windows user environment, not in project files."
Write-Output "Windows user environment variables are not encrypted; use this only on your own Windows account."
Write-Output "Existing non-empty values are preserved. For encrypted files, enter the original master key."
Write-Output ""

Set-RequiredUserVariable -Name "DB_PASSWORD" `
    -Prompt "Enter the MySQL password for DB_USERNAME (default: root)" `
    -Validator { param($value) $true }

Set-RequiredUserVariable -Name "DOCUMENT_ENCRYPTION_MASTER_KEY" `
    -Prompt "Enter the existing Base64-encoded 32-byte document encryption key" `
    -Validator {
        param($value)
        try {
            return [Convert]::FromBase64String($value).Length -eq 32
        }
        catch {
            return $false
        }
    }

Set-RequiredUserVariable -Name "GOOGLE_OAUTH_CLIENT_ID" `
    -Prompt "Enter the Google OAuth client ID" `
    -Validator { param($value) $true }

Set-RequiredUserVariable -Name "GOOGLE_OAUTH_CLIENT_SECRET" `
    -Prompt "Enter the Google OAuth client secret" `
    -Validator { param($value) $true }

Write-Output ""
Write-Output "Required values are saved. Fully exit VS Code and reopen it so it inherits them."
Write-Output "DB_URL and DB_USERNAME are optional; the application defaults to localhost MySQL and root."
