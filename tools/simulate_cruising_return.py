#!/usr/bin/env python3
"""Empirical model of RACING -> CRUISING return transition RPM behavior."""

from dataclasses import dataclass

DT = 0.016
TRANSITION_SECONDS = 1.0
FIXED_UPSHIFT_SECONDS = 0.10
COUPLED_RPM = {3: 6800.0, 4: 6200.0, 5: 5200.0, 6: 4200.0}


@dataclass
class Sample:
    t: float
    gear: int
    rpm: float
    shifting: bool


def sample_old(start_gear: int, target_gear: int, start_rpm: float) -> list[Sample]:
    samples: list[Sample] = []
    t = 0.0
    gear = start_gear
    rpm = start_rpm
    target_rpm = COUPLED_RPM[target_gear]
    shift_duration = TRANSITION_SECONDS / (target_gear - start_gear)

    while t < 2.0 and gear < target_gear:
        shift_start = rpm
        shift_target = COUPLED_RPM[gear + 1]
        elapsed = 0.0
        while elapsed < shift_duration:
            t += DT
            elapsed += DT
            progress = min(1.0, elapsed / shift_duration)
            rpm = shift_start + (shift_target - shift_start) * progress
            samples.append(Sample(t, gear, rpm, True))
        gear += 1
        rpm = COUPLED_RPM[gear]
        for _ in range(8):
            t += DT
            samples.append(Sample(t, gear, rpm, False))

    samples.append(Sample(t, gear, target_rpm, False))
    return samples


def sample_new(start_gear: int, target_gear: int, start_rpm: float) -> list[Sample]:
    samples: list[Sample] = []
    t = 0.0
    gear = start_gear
    target_rpm = COUPLED_RPM[target_gear]
    remaining = TRANSITION_SECONDS
    shifts_needed = target_gear - start_gear
    step = 1.0 / shifts_needed
    next_progress = step
    shifting = False
    shift_elapsed = 0.0

    while t < 2.0:
        t += DT
        remaining = max(0.0, remaining - DT)
        progress = 1.0 - (remaining / TRANSITION_SECONDS)
        rpm = start_rpm + (target_rpm - start_rpm) * progress

        if not shifting and gear < target_gear and progress + 1e-6 >= next_progress:
            shifting = True
            shift_elapsed = 0.0
            next_progress += step

        if shifting:
            shift_elapsed += DT
            if shift_elapsed >= FIXED_UPSHIFT_SECONDS:
                gear += 1
                shifting = False

        samples.append(Sample(t, gear, rpm, shifting))
        if remaining <= 0.0 and gear >= target_gear and not shifting:
            break

    return samples


def report(label: str, samples: list[Sample]) -> None:
    print(f"\n=== {label} ===")
    print("t(s)  gear  rpm   dRpm/dt  shifting")
    prev_rpm = samples[0].rpm
    frozen_ms = 0
    for index, sample in enumerate(samples):
        drpm = (sample.rpm - prev_rpm) / DT if index else 0.0
        if index % 8 == 0 or index == len(samples) - 1:
            print(f"{sample.t:4.2f}  {sample.gear:4d}  {sample.rpm:5.0f}  {drpm:7.0f}  {sample.shifting}")
        if not sample.shifting and abs(sample.rpm - prev_rpm) < 1.0:
            frozen_ms += int(DT * 1000)
        prev_rpm = sample.rpm
    print(f"total frozen-ish time (rpm delta < 1): {frozen_ms} ms")


if __name__ == "__main__":
    start_gear = 3
    target_gear = 6
    start_rpm = 7000.0
    report("OLD: 0.5s upshift + RPM trava no coupled entre marchas", sample_old(start_gear, target_gear, start_rpm))
    report("NEW: RPM desce linear 1s + upshifts rápidos espaçados", sample_new(start_gear, target_gear, start_rpm))
