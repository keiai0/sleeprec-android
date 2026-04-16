package io.github.keiai0.sleeprec

import io.github.keiai0.sleeprec.data.EventType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class EventTypeMapperTest {
    private fun scores(vararg pairs: Pair<Int, Float>) = FloatArray(521).also { for ((i, v) in pairs) it[i] = v }

    @Test fun snoring() = assertEquals(EventType.SNORING to 0.8f, EventTypeMapper.decide(scores(38 to 0.8f)))

    @Test fun speechWinsWhenHigher() =
        assertEquals(EventType.SLEEP_TALK, EventTypeMapper.decide(scores(0 to 0.96f, 38 to 0.04f)).first)

    @Test fun cough() = assertEquals(EventType.COUGH, EventTypeMapper.decide(scores(43 to 0.5f)).first)

    @Test fun animalUsesAnyOfItsClasses() =
        assertEquals(EventType.ANIMAL, EventTypeMapper.decide(scores(78 to 0.6f)).first)

    @Test fun belowMinIsOther() =
        assertEquals(EventType.OTHER to 0f, EventTypeMapper.decide(scores(38 to 0.29f)))

    @Test fun clappingIsOther() {
        // Silence(494)や Hands(56)は対応表にないので、種別には影響しない
        assertEquals(EventType.OTHER, EventTypeMapper.decide(scores(494 to 0.74f, 56 to 0.67f)).first)
    }

    @Test fun silenceDoesNotHideRealSound() =
        assertEquals(EventType.SNORING, EventTypeMapper.decide(scores(494 to 0.9f, 38 to 0.4f)).first)

    // 対応表のクラス番号が、同梱したラベルと合っていること(モデルを入れ替えたときの取り違え防止)
    @Test fun classIndicesMatchBundledLabels() {
        val labels = File("src/main/assets/yamnet_labels.txt").readLines()
        assertEquals(521, labels.size)
        val expected = mapOf(
            38 to "Snoring", 0 to "Speech", 12 to "Whispering", 42 to "Cough", 43 to "Throat clearing",
            55 to "Fart", 48 to "Walk, footsteps", 67 to "Animal", 68 to "Domestic animals, pets",
            69 to "Dog", 70 to "Bark", 76 to "Cat", 78 to "Meow",
        )
        for ((i, name) in expected) assertEquals(name, labels[i])
        assertEquals(expected.keys, EventTypeMapper.groups.values.flatMap { it.toList() }.toSet())
    }
}
