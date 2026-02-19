import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:segue/providers/audio_handler_provider.dart';
import 'package:segue/providers/database_provider.dart';
import 'package:segue/src/native/audio_analysis.dart';
import 'package:segue/view/main_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  AudioAnalysis.ensureInitialized();

  final container = ProviderContainer();
  container.read(databaseProvider);
  await container.read(audioHandlerFutureProvider.future);

  runApp(
    UncontrolledProviderScope(container: container, child: const SegueApp()),
  );
}

class SegueApp extends StatelessWidget {
  const SegueApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Segue',
      theme: ThemeData.dark(),
      home: const MainScreen(),
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: const [Locale('ja', 'JP')],
    );
  }
}
