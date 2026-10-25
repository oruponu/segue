package com.oruponu.segue.analysis

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalysisModule {
    @Binds abstract fun bindEngine(engine: SegueCoreEngine): AnalysisEngine
}
