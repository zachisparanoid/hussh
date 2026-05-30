package com.termforge.data.db

import androidx.room.TypeConverter

/** Room enum ↔ String converters. Enums are stored by [Enum.name] for forward-compatibility. */
class Converters {
    @TypeConverter fun protocolToString(v: Protocol): String = v.name
    @TypeConverter fun stringToProtocol(v: String): Protocol = Protocol.valueOf(v)

    @TypeConverter fun backspaceToString(v: BackspaceMode): String = v.name
    @TypeConverter fun stringToBackspace(v: String): BackspaceMode = BackspaceMode.valueOf(v)

    @TypeConverter fun keyTypeToString(v: KeyType): String = v.name
    @TypeConverter fun stringToKeyType(v: String): KeyType = KeyType.valueOf(v)

    @TypeConverter fun scopeToString(v: KeyBarScope): String = v.name
    @TypeConverter fun stringToScope(v: String): KeyBarScope = KeyBarScope.valueOf(v)

    @TypeConverter fun forwardToString(v: ForwardType): String = v.name
    @TypeConverter fun stringToForward(v: String): ForwardType = ForwardType.valueOf(v)

    @TypeConverter fun directionToString(v: TransferDirection): String = v.name
    @TypeConverter fun stringToDirection(v: String): TransferDirection = TransferDirection.valueOf(v)

    @TypeConverter fun transferStatusToString(v: TransferStatus): String = v.name
    @TypeConverter fun stringToTransferStatus(v: String): TransferStatus = TransferStatus.valueOf(v)
}
