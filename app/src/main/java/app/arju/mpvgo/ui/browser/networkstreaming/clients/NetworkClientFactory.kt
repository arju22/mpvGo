package app.arju.mpvgo.ui.browser.networkstreaming.clients

import app.arju.mpvgo.domain.network.NetworkConnection
import app.arju.mpvgo.domain.network.NetworkProtocol

object NetworkClientFactory {
  fun createClient(connection: NetworkConnection): NetworkClient =
    when (connection.protocol) {
      NetworkProtocol.SMB -> SmbClient(connection)
      NetworkProtocol.FTP -> FtpClient(connection)
      NetworkProtocol.WEBDAV -> WebDavClient(connection)
    }
}
