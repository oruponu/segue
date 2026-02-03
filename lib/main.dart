import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter/material.dart';
import 'package:segue/providers/audio_handler_provider.dart';
import 'package:segue/view/main_screen.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final container = ProviderContainer();
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
    );
  }
}
