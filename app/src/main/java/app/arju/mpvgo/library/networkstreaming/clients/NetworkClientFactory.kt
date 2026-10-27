package app.arju.mpvgo.library.networkstreaming.clients

import app.arju.mpvgo.core.domain.network.NetworkConnection
import app.arju.mpvgo.core.domain.network.NetworkProtocol

object NetworkClientFactory {
  fun createClient(connection: NetworkConnection): NetworkClient =
    when (connection.protocol) {
      NetworkProtocol.SMB -> SmbClient(connection)
      NetworkProtocol.FTP -> FtpClient(connection)
      NetworkProtocol.WEBDAV -> WebDavClient(connection)
    }
}
