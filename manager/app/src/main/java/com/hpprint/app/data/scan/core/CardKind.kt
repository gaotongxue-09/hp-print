package com.hpprint.app.data.scan.core

import androidx.annotation.StringRes
import com.hpprint.app.R

/**
 * 可打印的证件类型：决定 A4 排版时的实物尺寸与圆角，不改变已矫正的图片内容。
 * 与 LocalScan 的 CardType 对齐（竖版证件在这里直接以竖向宽高表示）。
 */
enum class CardKind(
    @StringRes val labelRes: Int,
    val widthMm: Float,
    val heightMm: Float,
    val cornerMm: Float,
) {
    ID_CARD(R.string.hp_card_kind_id, 85.6f, 54f, 3.18f),
    BANK_CARD(R.string.hp_card_kind_bank, 85.6f, 54f, 3.18f),
    PASSPORT(R.string.hp_card_kind_passport, 88f, 125f, 3.18f),
    DRIVER_LICENSE(R.string.hp_card_kind_driving_license, 60f, 88f, 3.0f),
    DRIVER_PERMIT(R.string.hp_card_kind_driver_permit, 60f, 88f, 3.0f),
    RESIDENCE_BOOKLET(R.string.hp_card_kind_residence, 54f, 85.6f, 3.18f),
    RESIDENCE_TWO(R.string.hp_card_kind_residence_book, 143f, 105f, 0f),
    BUSINESS_LICENSE(R.string.hp_card_kind_business, 210f, 297f, 0f);

    val aspect: Float get() = widthMm / heightMm

    companion object {
        val all: List<CardKind> = entries
        val default: CardKind = ID_CARD
    }
}
