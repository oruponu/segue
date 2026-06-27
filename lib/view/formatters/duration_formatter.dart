String formatDuration(Duration? duration) {
  if (duration == null) return '--:--';

  String twoDigits(int n) => n.toString().padLeft(2, '0');
  final hours = duration.inHours;
  final minutes = duration.inMinutes.remainder(60);
  final seconds = twoDigits(duration.inSeconds.remainder(60));
  if (hours > 0) {
    return '$hours:${twoDigits(minutes)}:$seconds';
  }
  return '$minutes:$seconds';
}
