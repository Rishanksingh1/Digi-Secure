# Digi Secure

Digi Secure is a Spring Boot web application for storing, organizing, previewing, and sharing documents. File contents are encrypted before they are saved to local storage. Optional local OCR can suggest a document type and category after upload.

> **Important:** Digi Secure is a self-hosted application, not a managed cloud storage service. You are responsible for its configuration, server security, backups, and encryption-key protection.

## Features

- Account registration with email verification
- Google sign-in and multi-factor authentication
- Encrypted document storage
- Document previews and document organization
- Optional local OCR for images and PDFs, including suggested document types and categories
- Configurable share links with expiration, use limits, optional PIN or email-code verification, and revocation
- Watermarked previews when a share link does not allow downloads
- Email verification, recipient codes, and sharing alerts when SMTP is configured

## Technology

- Java 25
- Spring Boot 4.1.1
- Spring Security, Spring Data JPA, and Thymeleaf
- MySQL
- Maven
- Tesseract OCR (optional)

## Requirements

- Java 25
- MySQL
- A Google OAuth client for Google sign-in
- An SMTP account for email verification and email-based share links
- Tesseract OCR with English language data, if you want OCR

## Run locally on Windows

### 1. Get the code

```powershell
git clone https://github.com/Rishanksingh1/Digi-Secure.git
cd Digi-Secure
```

### 2. Create the database

Create a MySQL database:

```sql
CREATE DATABASE secure_digital_locker;
```

The application defaults to `jdbc:mysql://localhost:3306/secure_digital_locker` and the MySQL username `root`. Make sure MySQL is running and that the configured user can access this database.

### 3. Configure required settings

Open PowerShell in the project directory and run:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\configure-user-environment.ps1
```

The script prompts for the database password, document encryption key, and Google OAuth client credentials. It stores values in your Windows user environment; these variables are **not encrypted** by Windows. Use this method only on a trusted account and device.

For Google OAuth, configure an OAuth client in Google Cloud and add this callback URL to the client:

```text
http://localhost:8080/login/oauth2/code/google
```

Never place passwords, OAuth secrets, or encryption keys in source files or commit them to Git.

### 4. Start the application

```powershell
.\mvnw.cmd spring-boot:run
```

Open [http://localhost:8080](http://localhost:8080) in your browser. If you have just saved Windows environment variables, fully close and reopen VS Code or your terminal before starting the application.

## Configuration reference

| Variable | Required? | Purpose |
|---|---:|---|
| `DB_PASSWORD` | Yes | Password for the configured MySQL user |
| `DB_URL` | No | MySQL JDBC URL; defaults to `jdbc:mysql://localhost:3306/secure_digital_locker` |
| `DB_USERNAME` | No | MySQL username; defaults to `root` |
| `DOCUMENT_ENCRYPTION_MASTER_KEY` | Yes | Base64-encoded, randomly generated 32-byte encryption key |
| `GOOGLE_OAUTH_CLIENT_ID` | Yes | Google OAuth client ID |
| `GOOGLE_OAUTH_CLIENT_SECRET` | Yes | Google OAuth client secret |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | For email | SMTP server settings |
| `SMTP_AUTH`, `SMTP_STARTTLS`, `SMTP_FROM` | No | Optional SMTP authentication, TLS, and sender settings |
| `APP_PUBLIC_BASE_URL` | No | Base URL used when generating links that must open from another device |
| `TESSERACT_COMMAND` | For custom OCR path | Tesseract executable path; defaults to the standard Windows installation path |
| `LEGACY_DOCUMENT_ENCRYPTION_SECRET` | Legacy files only | Temporary setting for reading documents saved using the previous storage format |

### Generate a new encryption key

For a **new installation**, generate a random key in PowerShell:

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$rng.GetBytes($bytes)
[Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Save the output in a secure password manager, then provide it when the setup script prompts for `DOCUMENT_ENCRYPTION_MASTER_KEY`.

**Keep this key backed up securely.** Losing it can make files encrypted with it unreadable. Do not generate a replacement for an existing installation unless you have a planned key migration.

## Email setup

Email verification and email-code share links require a working SMTP configuration. For Gmail:

1. Enable 2-Step Verification on the Google account.
2. Create a Google App Password.
3. Run this script from PowerShell in the project directory:

   ```powershell
   .\configure-gmail-smtp.ps1
   ```

Use the App Password, not your regular Google password. Restart the application after changing SMTP settings. Email-code links require SMTP to be configured; access codes are sent to the recipient address specified for the link.

## Document storage and sharing

Uploaded file contents are encrypted with AES-GCM and stored under `storage/encrypted`. The master key is required to decrypt them. Keep the encrypted files and the matching key backed up securely.

Share links can be configured with an expiration time, maximum use count, optional PIN or recipient email code, and download restrictions. Links can be revoked. When downloads are disabled, the app provides a watermarked preview instead of the original file.

A file that a recipient downloads or receives as an attachment can be kept or forwarded. Link expiry and download settings cannot control copies outside the application. No browser can prevent screenshots, photography, or capture of displayed content.

### Security considerations

- Protect the encryption key and database credentials. Do not commit them, paste them into issues, or include them in source archives.
- File encryption does not by itself encrypt database metadata, backups, logs, network traffic, or files after they are downloaded.
- For access beyond a trusted local network, deploy behind HTTPS and use secure server, database, and backup practices. Do not expose a development instance directly to the public internet.
- `LEGACY_DOCUMENT_ENCRYPTION_SECRET` is only for migrating old-format documents. A previous legacy secret was exposed in source; treat it as compromised. Migrate any needed legacy documents to the current format and remove the variable. Do not rely on that secret to protect new documents.
- Do not use real or sensitive personal documents in a public demo or untrusted deployment.

## Optional OCR

OCR runs locally after upload and is optional. Install Tesseract with English language data. If it is not installed at the standard Windows path, set `TESSERACT_COMMAND` to the executable’s path.

Selectable PDF text can be extracted directly. Scanned PDFs and images use Tesseract; at most the first five pages of a scanned PDF are processed. Recognition runs in the background, so refresh the dashboard to see suggestions. Uploads can still succeed when OCR is unavailable. Extracted personal metadata is not persisted by the recognition service.

## Tests

Tests use an in-memory H2 database and do not require a running MySQL server:

```powershell
.\mvnw.cmd test
```

## Source archive

To create a source ZIP that excludes build output, encrypted storage, local environment files, and IDE settings, run:

```powershell
.\package-source.ps1
```

## More setup details

See [HELP.md](HELP.md) for additional Windows setup instructions, troubleshooting, local-network sharing guidance, and email configuration details.

## License

No license is currently included. Until a license is added, do not assume that public availability grants permission to reuse, modify, or distribute this project.
