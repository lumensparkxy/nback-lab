#!/usr/bin/env python3
"""Run the critical CI selection or the complete Android suite, failing on missing evidence."""
from pathlib import Path
import argparse
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
# Keep selectors explicit so coverage changes are reviewable; no test implementation is removed.
CRITICAL = (
    'LaunchSmokeTest#instructionsAndWarmupAreAccessible',
    'LaunchSmokeTest#realTimedSessionCompletesAndResultsSurviveRecreationAndBackground',
    'LaunchSmokeTest#pauseWithoutStopInterruptsAndRestartBeginsWarmup',
    'SessionInteractionTest#warmupNeutralFeedbackDuplicateInputAndNewTrial',
    'SessionInteractionTest#holdingDoesNotRespondOrRepeatAndReleaseBelongsToNewTrial',
    'PracticeInteractionTest#practiceExplanationsAndExplicitCompletionActionsStaySeparate',
    'MultiTypeUiTest#independentTogglesPreventEmptySelectionAndOnlyActiveButtonsAppear',
    'MultiTypeUiTest#resultsExposeEachTypeWithoutFabricatingInactiveScores',
    'MultiTypeLifecycleTest#combinedConfigurationSurvivesRecreationAndInterruptsAsOneSession',
    'IntervalLifecycleTest#intervalSnapshotSurvivesRecreationInterruptionAndRestart',
    'LevelSettingsTest#dataStoreRoundTripsMissingInvalidTypesRangesAndCorruption',
    'HistoryCoordinatorTest#everyTerminalEventCapturesOnceAtEveryLevelAndViewModelClearDoesNotCancelSave',
    'HistoryCoordinatorTest#restartAndPlayAgainUseNewIdsPracticeAndPartialSessionsNeverSave',
    'HistoryCoordinatorTest#clearCutoffOrdersPendingWritesAndNeverResurrectsOldHandles',
    'HistoryUiTest#emptyFiltersClearCancelAndGlobalClearRetainDifficulty',
    'RoomHistoryTest#creationReopenOrderingIdenticalRetryConflictAndAtomicClear',
    'RoomHistoryTest#corruptTruncatedEmptyAndIncompatibleStoresArePreservedAcrossEveryRetry',
    'MultiTypeStorageTest#realV1MigrationPreservesEveryFieldAndSupportsNewModesAfterReopen',
    'IntervalStorageTest#v2MigrationPreservesOriginalFieldsAndNewTimelinesRoundTrip',
    'IntervalStorageTest#intervalPreferencesRoundTripAndInvalidFieldDoesNotResetLevelOrTypes',
)
PREFIX = 'com.maswadkar.nback.'


def report_signatures(directory):
    return {p: (p.stat().st_mtime_ns, p.stat().st_size) for p in directory.rglob('TEST-*.xml')}


def check_reports(paths, expected):
    passed = set()
    for path in paths:
        for test in ET.parse(path).getroot().iter('testcase'):
            name = test.get('classname', '') + '#' + test.get('name', '')
            if any(test.find(tag) is not None for tag in ('failure', 'error', 'skipped')):
                raise ValueError(f'Test did not pass: {name}')
            passed.add(name)
    missing = set(expected) - passed
    if not passed or missing:
        raise ValueError(f'Missing executed tests: {sorted(missing)}')
    return len(passed)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('suite', choices=('critical', 'full'), nargs='?', default='critical')
    args = parser.parse_args()
    expected = [PREFIX + test for test in CRITICAL]
    command = [str(ROOT / 'scripts/emulator-test.sh')]
    if args.suite == 'critical':
        command.append('-Pandroid.testInstrumentationRunnerArguments.class=' + ','.join(expected))
    reports = ROOT / 'app/build/outputs/androidTest-results/connected'
    before = report_signatures(reports)
    result = subprocess.run(command, cwd=ROOT)
    if result.returncode:
        return result.returncode
    fresh = [p for p, signature in report_signatures(reports).items() if before.get(p) != signature]
    try:
        count = check_reports(fresh, expected)
    except (ValueError, ET.ParseError) as error:
        print(f'Android CI evidence failed: {error}', file=sys.stderr)
        return 1
    print(f'Android {args.suite} suite: {count} tests passed; all {len(expected)} critical tests executed.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
