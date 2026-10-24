package app.arju.mpvgo.database.converters

import androidx.room.TypeConverter
import app.arju.mpvgo.domain.network.NetworkProtocol

/**
 * Type converter for NetworkProtocol enum
 */
class NetworkProtocolConverter {
  @TypeConverter
  fun fromNetworkProtocol(protocol: NetworkProtocol): String = protocol.name

  @TypeConverter
  fun toNetworkProtocol(value: String): NetworkProtocol = NetworkProtocol.valueOf(value)
}
