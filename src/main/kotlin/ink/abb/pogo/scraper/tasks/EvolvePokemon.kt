/**
 * Pokemon Go Bot  Copyright (C) 2016  PokemonGoBot-authors (see authors.md for more information)
 * This program comes with ABSOLUTELY NO WARRANTY;
 * This is free software, and you are welcome to redistribute it under certain conditions.
 *
 * For more information, refer to the LICENSE file in this repositories root directory
 */

package ink.abb.pogo.scraper.tasks

import ink.abb.pogo.scraper.Bot
import ink.abb.pogo.scraper.Context
import ink.abb.pogo.scraper.Settings
import ink.abb.pogo.scraper.Task
import ink.abb.pogo.scraper.util.Log
import POGOProtos.Inventory.Item.ItemIdOuterClass.ItemId
import POGOProtos.Networking.Responses.UseItemXpBoostResponseOuterClass
import com.pokegoapi.api.map.pokemon.EvolutionResult
import com.pokegoapi.api.pokemon.Pokemon
import ink.abb.pogo.scraper.util.pokemon.getStatsFormatted

/**
 * @author Michael Meehan (Javapt)
 */

class  EvolvePokemon : Task {
    var lastUsedLuckyEgg = 0L;
    var lastBulkedEvolveCount = 0;
    override fun run(bot: Bot, ctx: Context, settings: Settings) {
        if (settings.autoEvolve.isEmpty()) {
            return
        }

        val groupedPokemon = ctx.api.inventories.pokebank.pokemons.groupBy { it.pokemonId }
        val autoEvolve = settings.autoEvolve
        val canEvolve = groupedPokemon.filter {
            val candyNeeded = settings.candyRequiredByPokemon[it.key.number]
            candyNeeded != null && candyNeeded > 0 && autoEvolve.contains(it.key.name) && it.value.first().candy >= candyNeeded
        }
        if (canEvolve.isEmpty() || (canEvolve.size < settings.bulkEvolveCount && settings.bulkEvolveCount > 0)) {
            if (!canEvolve.isEmpty() && lastBulkedEvolveCount != canEvolve.size) {
                lastBulkedEvolveCount = canEvolve.size
                Log.blue("Bulked evolve (" + canEvolve.size + "/" + settings.bulkEvolveCount + ")")
            }
            return
        }
        if (settings.bulkEvolveUseEgg && lastUsedLuckyEgg < System.currentTimeMillis()) {
            val luckyEgg = ctx.api.inventories.itemBag.getItem(ItemId.ITEM_LUCKY_EGG);
            if (luckyEgg.count > 0) {
                ctx.api.inventories.itemBag.useLuckyEgg()
                Log.blue("Evolving using Lucky Egg.")
                lastUsedLuckyEgg = System.currentTimeMillis() + 30 * 60 * 1000;
            }
        }
        canEvolve.forEach {
            val sorted = it.value.sortedByDescending { it.id }
            val candyNeeded = settings.candyRequiredByPokemon[it.key.number]
            if (candyNeeded != null) {
                for ((index, pokemon) in sorted.withIndex()) {
                    if (pokemon.candy < candyNeeded) {
                        break;
                    }
                    val result = pokemon.evolve()

                    if (result == null) {
                        Log.red("Failed to evolve ${pokemon.pokemonId.name}")
                        return
                    }

                    if (result.isSuccessful) {
                        val evolvedPokemon = result.getEvolvedPokemon();
                        Log.blue("Evolving ${pokemon.pokemonId.name} to ${evolvedPokemon.pokemonId.name.toLowerCase().replace("_", " ").capitalize()} (CP : ${evolvedPokemon.cp} | ${evolvedPokemon.getStatsFormatted()}) using (${candyNeeded}x ${evolvedPokemon.pokemonFamily.name.toLowerCase().replace("family_", "")} candy), received [${result.getExpAwarded()}xp].")
                    }
                }
            }
        }
    }
}