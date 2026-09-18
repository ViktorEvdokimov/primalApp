package com.primalapp.domain

import com.primalapp.model.campaign.Material
import com.primalapp.model.campaign.Plant
import com.primalapp.model.campaign.Element

sealed class ExchangeResult {
    data class Valid(val message: String = "") : ExchangeResult()
    data class Invalid(val reason: String) : ExchangeResult()
}

interface ResourceExchangeValidator {

    fun canExchangeMaterials(from: Map<Material, Int>, to: Map<Material, Int>): ExchangeResult
    fun canExchangePlants(from: Map<Plant, Int>, to: Map<Plant, Int>): ExchangeResult

    fun canExchangeElements(from: Map<Element, Int>, to: Map<Element, Int>): ExchangeResult
}
