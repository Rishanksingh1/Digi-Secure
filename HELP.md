# Getting Started

### Required environment variables

The application does not keep credentials or encryption keys in the source tree. Configure these variables before running it:

* `DB_PASSWORD` (and optionally `DB_URL` and `DB_USERNAME`) for MySQL.
* `DOCUMENT_ENCRYPTION_MASTER_KEY`, a Base64-encoded, randomly generated 32-byte key. Keep a protected backup; losing it makes newly encrypted files unreadable.
* `GOOGLE_OAUTH_CLIENT_ID` and `GOOGLE_OAUTH_CLIENT_SECRET` for Google sign-in.
* `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS`, and `SMTP_FROM` for account-verification messages, recipient email codes, and share-access alerts. For Gmail, enable 2-Step Verification and run `.\configure-gmail-smtp.ps1` in PowerShell; use a Google App Password, not your regular Google password. Fully restart VS Code and the application after configuring. The code is delivered to the recipient email address set in the share link. Email-code link creation is disabled until SMTP is configured. Verification mail is required; failure does not create an account. Share alerts are logged as errors if delivery fails after an access event.
* `APP_PUBLIC_BASE_URL` is optional for local-only use. To open shared links from another device on the same network, set it to this PC's LAN address, for example `http://192.168.1.25:8080` (find the address with `ipconfig` and use the active Wi-Fi/Ethernet IPv4 address). Allow Java through Windows Firewall on a private network if prompted, and ensure both devices are on the same Wi-Fi. Regenerate links after changing this value. `localhost` links only work on this PC.
* `LEGACY_DOCUMENT_ENCRYPTION_SECRET` is optional and only needed to decrypt files created by the previous storage format. After setting it, download each legacy file, upload it as a new document, verify the new copy, and delete the old document. Then remove this variable. The former key has been exposed in source, so treat it as compromised.

### One-time Windows setup

To avoid entering configuration every time, save the required values as Windows user environment variables:

1. Open this project in VS Code and choose **Terminal > New Terminal**. Confirm the terminal is in the project folder (the folder containing `pom.xml` and this script).
2. Run the following in PowerShell. The execution-policy change applies only to this PowerShell process; it does not change the computer's policy:
   ```powershell
   Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
   .\configure-user-environment.ps1
   ```
3. For each value prompt, copy the value first, then press **Enter** to read it from the clipboard without displaying it. Alternatively, type **M** and press **Enter** to enter it manually in the hidden prompt. This works for long client IDs and secrets without typing them character by character.
4. Enter the **existing** Base64 master key. Do not generate a replacement if you need to open files already encrypted by this application. The script checks that it decodes to exactly 32 bytes.
5. Enter the Google OAuth client ID and client secret using the clipboard option or hidden manual entry. Clear the clipboard after setup if it contains a secret.
6. When the script reports completion, close every VS Code window completely and reopen VS Code and this project. New processes need to start after the variables are saved to inherit them.
7. Start the application again. It uses `DB_URL` and `DB_USERNAME` if set; otherwise, it connects to `jdbc:mysql://localhost:3306/secure_digital_locker` as `root`.

The script preserves any required value already saved for your Windows user, so it only prompts for missing values. If an existing value is wrong, update it in **Windows Settings > System > About > Advanced system settings > Environment Variables... > User variables**, then restart VS Code. Do not put credentials in `application.properties`, commit them, or send them in chat. Windows user environment variables are stored in your Windows profile and are not an encrypted secret store; use this only on a trusted account/device.

The setup script does not create the MySQL database or change its password. MySQL must be running, and the configured user/password must be able to access the `secure_digital_locker` database. Google OAuth credentials must belong to a client configured for this application.

In PowerShell, generate a master key for the current terminal session with:

```powershell
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$bytes = New-Object byte[] 32
$rng.GetBytes($bytes)
$env:DOCUMENT_ENCRYPTION_MASTER_KEY = [Convert]::ToBase64String($bytes)
$rng.Dispose()
```

Set the remaining required variables from your protected credential store before starting the application. Do not commit them or include them in a source archive.
If the previous configuration was pushed or shared, rotate the MySQL password and Google OAuth client secret; removing them from this source does not revoke them.

### Creating a source archive

Run `.\package-source.ps1` from the project directory to create a source zip without `target/`, `storage/`, local environment files, or IDE settings.

### Sharing documents

Use **Share file** to send the decrypted document copy through the browser/device share panel, such as WhatsApp when the platform supports file sharing. Recipients can keep or forward this copy; link expiration and download permissions cannot control files sent as attachments. Secure links support expiry, download permission, maximum uses, optional PIN/email OTP, immediate revocation, per-link audit counts, and owner alerts. When downloads are disabled, the original is not served: images and the first PDF page are converted to a watermarked preview. No browser can prevent screenshots, photography, or capture of displayed content. Access emails and alerts require SMTP configuration.

For Gmail email-code links, the owner must configure Gmail SMTP with an App Password before creating or reopening the link. Gmail requires 2-Step Verification before it allows App Passwords. When `configure-gmail-smtp.ps1` asks for the App Password, copy it from Google, press **Enter** to paste it securely from the clipboard, or type **M** for hidden manual entry. Clear the clipboard after setup. Codes are sent to the recipient address configured when the link is created; check that inbox and its spam folder. A code is limited to one send per minute and expires after 10 minutes. After changing SMTP settings, restart the application before retrying.

The test classpath configures an in-memory H2 database, so `.\mvnw.cmd test` does not require MySQL credentials or a running database.

### Local document recognition
Uploads are encrypted and saved before document recognition runs in the background. Selectable text is extracted directly from PDFs; scanned PDFs and images use the local Tesseract OCR engine. Install Tesseract with English language data and make `tesseract` available on `PATH`, then restart the application. To use a non-default executable, set `TESSERACT_COMMAND` to its path.

Recognition classifies common identity documents, marksheets, and certificates, and suggests a category. For scanned PDFs, only the first five pages are processed. The upload confirmation does not wait for OCR; refresh the dashboard after recognition finishes to see an updated type and category. Extracted personal metadata is not persisted. If Tesseract is unavailable or cannot read a file, the upload still completes and the recognition notice explains the issue.

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.1/maven-plugin/build-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.1/reference/web/servlet.html)
* [Spring Security](https://docs.spring.io/spring-boot/4.1.1/reference/web/spring-security.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.1/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Thymeleaf](https://docs.spring.io/spring-boot/4.1.1/reference/web/servlet.html#web.servlet.spring-mvc.template-engines)
* [Spring Boot DevTools](https://docs.spring.io/spring-boot/4.1.1/reference/using/devtools.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Securing a Web Application](https://spring.io/guides/gs/securing-web/)
* [Spring Boot and OAuth2](https://spring.io/guides/tutorials/spring-boot-oauth2/)
* [Authenticating a User with LDAP](https://spring.io/guides/gs/authenticating-ldap/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Accessing data with MySQL](https://spring.io/guides/gs/accessing-data-mysql/)
* [Handling Form Submission](https://spring.io/guides/gs/handling-form-submission/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.
