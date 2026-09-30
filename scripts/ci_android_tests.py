#!/usr/bin/env python3
"""Run the critical CI selection or the complete Android suite, failing on missing evidence."""
from pathlib import Path
import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from harness_evidence import android_record, junit_methods, same_source, source_identity

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
    'AdsPrivacyTest#consentAndRequestConfigurationAreIndependentAndPermissionGated',
    'AdsPrivacyTest#lateLoadsPrivacyChangesAndDeferredFormsNeverInterruptPlay',
    'LengthStorageTest#v3MigrationPreservesAllLegacyRulesAndNewLengthsRoundTrip',
    'LengthStorageTest#invalidRawLengthsAndOutcomesCannotBeReadOverwrittenOrCleared',
    'SessionExperienceTest#settingsHelpAndVariableCompletionUseSameSnapshotAndSaveOnce',
    'SessionExperienceTest#equalContentRefreshFinishesPreparingEmptyAndPopulatedHistory',
)
PREFIX = 'com.maswadkar.nback.'


def full_inventory():
    """Discover this repository's one-JUnit-class-per-file Kotlin test methods.

    Reject unsupported declarations instead of silently certifying partial coverage.
    """
    expected = []
    for path in sorted((ROOT / 'app/src/androidTest/java').rglob('*.kt')):
        source = path.read_text()
        annotations = re.findall(r'@(?:\w+\.)*Test\b', source)
        if not annotations:
            continue
        package = re.search(r'^package\s+([\w.]+)', source, re.MULTILINE)
        classes = re.findall(r'\bclass\s+(\w+Test)\b', source)
        methods = re.findall(r'@(?:org\.junit\.)?Test\s+fun\s+(\w+)\s*\(', source)
        if not package or classes != [path.stem] or len(methods) != len(annotations):
            raise ValueError(f'Unsupported test declarations: {path}')
        expected.extend(f'{package.group(1)}.{path.stem}#{method}' for method in methods)
    if not expected or len(set(expected)) != len(expected):
        raise ValueError('Full test inventory is empty or contains duplicate methods')
    return expected


def report_signatures(directory):
    return {p: (p.stat().st_mtime_ns, p.stat().st_size) for p in directory.rglob('TEST-*.xml')}


def check_reports(paths, expected):
    return len(junit_methods(paths, expected))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('suite', choices=('critical', 'full'), nargs='?', default='critical')
    args = parser.parse_args()
    try:
        expected = full_inventory() if args.suite == 'full' else [PREFIX + test for test in CRITICAL]
        source_before = source_identity(ROOT)
    except (ValueError, OSError, subprocess.TimeoutExpired) as error:
        print(f'Android CI inventory failed: {error}', file=sys.stderr)
        return 1
    command = [str(ROOT / 'scripts/emulator-test.sh')]
    if args.suite == 'critical':
        command.append('-Pandroid.testInstrumentationRunnerArguments.annotation=' + PREFIX + 'CriticalCi')
    reports = ROOT / 'app/build/outputs/androidTest-results/connected'
    before = report_signatures(reports)
    result = subprocess.run(command, cwd=ROOT)
    if result.returncode:
        android_record(ROOT, args.suite, source_before, None, expected, [], result.returncode,
                       'Android command failed; no passing evidence accepted')
        return result.returncode
    fresh = [p for p, signature in report_signatures(reports).items() if before.get(p) != signature]
    try:
        count = check_reports(fresh, expected)
        source_after = source_identity(ROOT)
        if not same_source(source_before, source_after):
            raise ValueError('Source changed during Android validation')
        record = android_record(ROOT, args.suite, source_before, source_after, expected, fresh, 0)
    except (ValueError, OSError, ET.ParseError, subprocess.TimeoutExpired) as error:
        android_record(ROOT, args.suite, source_before, None, expected, [], 1,
                       'Android evidence rejected')
        print(f'Android CI evidence failed: {error}', file=sys.stderr)
        return 1
    print(f'Android {args.suite} suite: {count} tests passed; all {len(expected)} expected tests executed.')
    print(f'Android evidence: {record}')
    return 0


if __name__ == '__main__':
    sys.exit(main())
