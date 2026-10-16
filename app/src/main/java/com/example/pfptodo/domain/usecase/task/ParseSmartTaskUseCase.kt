package com.example.pfptodo.domain.usecase.task

import com.example.pfptodo.domain.model.TaskParseResult
import com.example.pfptodo.domain.parser.TaskParser
import java.time.Clock

/**
 * UseCase that parses a natural language input string into structured task data.
 */
class ParseSmartTaskUseCase(
    private val parser: TaskParser
) {
    operator fun invoke(input: String, clock: Clock = Clock.systemDefaultZone()): TaskParseResult {
        return parser.parse(input, clock)
    }
}
