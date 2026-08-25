package com.offhand.action

import com.offhand.data.ActionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Properties
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

data class SmtpConfig(
    val host: String,
    val port: Int,
    val username: String,
    val password: String,
    val from: String,
    val starttls: Boolean,
)

/**
 * SMTP delivery. The connect attempt doubles as the execution-time
 * reachability check — the UI banner is never consulted (master spec §8).
 */
class EmailExecutor(private val config: SmtpConfig) : ActionExecutor {

    override suspend fun execute(entity: ActionEntity, slots: StoredSlots): ExecOutcome =
        withContext(Dispatchers.IO) {
            val to = slots.recipientEmail
                ?: return@withContext ExecOutcome.Failure("No recipient")

            try {
                val props = Properties().apply {
                    put("mail.smtp.host", config.host)
                    put("mail.smtp.port", config.port.toString())
                    put("mail.smtp.connectiontimeout", "10000")
                    put("mail.smtp.timeout", "20000")
                    if (config.starttls) put("mail.smtp.starttls.enable", "true")
                    if (config.username.isNotBlank()) put("mail.smtp.auth", "true")
                }
                val session = Session.getInstance(props)
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(config.from))
                    setRecipient(Message.RecipientType.TO, InternetAddress(to))
                    subject = slots.subject?.takeIf { it.isNotBlank() } ?: "(no subject)"
                    val bodyText = slots.body.orEmpty()
                    val attachment = slots.attachmentPath?.let(::File)?.takeIf { it.exists() }
                    if (attachment != null) {
                        setContent(
                            MimeMultipart().apply {
                                addBodyPart(MimeBodyPart().apply { setText(bodyText) })
                                addBodyPart(MimeBodyPart().apply { attachFile(attachment) })
                            },
                        )
                    } else {
                        setText(bodyText)
                    }
                }

                if (config.username.isNotBlank()) {
                    val transport = session.getTransport("smtp")
                    try {
                        transport.connect(config.host, config.port, config.username, config.password)
                        transport.sendMessage(message, message.allRecipients)
                    } finally {
                        transport.close()
                    }
                } else {
                    Transport.send(message)
                }
                ExecOutcome.Success
            } catch (e: MessagingException) {
                ExecOutcome.Failure(e.message ?: "SMTP error")
            } catch (e: Exception) {
                ExecOutcome.Failure("Send failed: ${e.javaClass.simpleName}")
            }
        }
}
