package com.ledgerly.app.data.db

import androidx.room.TypeConverter
import com.ledgerly.app.domain.model.BudgetPeriod
import com.ledgerly.app.domain.model.TxType

class Converters {
    @TypeConverter
    fun txTypeToString(value: TxType): String = value.name

    @TypeConverter
    fun stringToTxType(value: String): TxType = TxType.valueOf(value)

    @TypeConverter
    fun budgetPeriodToString(value: BudgetPeriod): String = value.name

    @TypeConverter
    fun stringToBudgetPeriod(value: String): BudgetPeriod = BudgetPeriod.valueOf(value)
}