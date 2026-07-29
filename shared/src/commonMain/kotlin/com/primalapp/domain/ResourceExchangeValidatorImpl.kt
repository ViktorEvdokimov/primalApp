package com.primalapp.domain

import com.primalapp.model.campaign.Element
import com.primalapp.model.campaign.Material
import com.primalapp.model.campaign.Plant

class ResourceExchangeValidatorImpl : ResourceExchangeValidator {

    override fun canExchangeMaterials(from: Map<Material, Int>, to: Map<Material, Int>): ExchangeResult {
        return validateExchange(from.values.sum(), to.values.sum())
    }

    override fun canExchangePlants(from: Map<Plant, Int>, to: Map<Plant, Int>): ExchangeResult {
        return validateExchange(from.values.sum(), to.values.sum())
    }

    override fun canExchangeElements(from: Map<Element, Int>, to: Map<Element, Int>): ExchangeResult {
        return validateExchange(from.values.sum(), to.values.sum())
    }

    private fun validateExchange(fromTotal: Int, toTotal: Int): ExchangeResult {
        if (fromTotal == 0) return ExchangeResult.Invalid("Не указано, что вы отдаёте")
        if (toTotal == 0) return ExchangeResult.Invalid("Не указано, что вы получаете взамен")
        if (fromTotal != toTotal) {
            return ExchangeResult.Invalid(
                "Обмен должен быть 1:1. Вы отдаёте $fromTotal, получаете $toTotal"
            )
        }
        return ExchangeResult.Valid()
    }
}
