/*
 * This file is part of NavalPlan
 *
 * Copyright (C) 2009-2010 Fundación para o Fomento da Calidade Industrial e
 *                         Desenvolvemento Tecnolóxico de Galicia
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.navalplanner.business.planner.entities.allocationalgorithms;

import static org.navalplanner.business.workingday.EffortDuration.zero;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang.Validate;
import org.joda.time.LocalDate;
import org.navalplanner.business.common.ProportionalDistributor;
import org.navalplanner.business.planner.entities.DayAssignment;
import org.navalplanner.business.planner.entities.ResourceAllocation;
import org.navalplanner.business.planner.entities.Task;
import org.navalplanner.business.workingday.EffortDuration;
import org.navalplanner.business.workingday.ResourcesPerDay;

public abstract class AllocatorForSpecifiedResourcesPerDayAndHours {

    private final Task task;

    private List<ResourcesPerDayModification> allocations;

    private Map<ResourcesPerDayModification, List<DayAssignment>> resultAssignments = new HashMap<ResourcesPerDayModification, List<DayAssignment>>();

    public AllocatorForSpecifiedResourcesPerDayAndHours(Task task,
            List<ResourcesPerDayModification> allocations) {
        this.task = task;
        this.allocations = allocations;
        initializeResultsMap();
    }

    private void initializeResultsMap() {
        for (ResourcesPerDayModification r : allocations) {
            resultAssignments.put(r, new ArrayList<DayAssignment>());
        }
    }

    public LocalDate untilAllocating(EffortDuration effortToAllocate) {
        LocalDate taskStart = LocalDate.fromDateFields(task.getStartDate());
        LocalDate start = (task.getFirstDayNotConsolidated().compareTo(
                taskStart) >= 0) ? task.getFirstDayNotConsolidated()
                : taskStart;
        int i = 0;
        int maxDaysElapsed = 0;
        for (EffortPerAllocation each : effortPerAllocation(start,
                effortToAllocate)) {
            int daysElapsedForCurrent = untilAllocating(start, each.allocation,
                    each.duration);
            maxDaysElapsed = Math.max(maxDaysElapsed, daysElapsedForCurrent);
            i++;
        }
        setAssignmentsForEachAllocation();
        return start.plusDays(maxDaysElapsed);
    }

    private List<EffortPerAllocation> effortPerAllocation(LocalDate start,
            EffortDuration toBeAssigned) {
        return new HoursPerAllocationCalculator(allocations)
                .calculateEffortsPerAllocation(start, toBeAssigned);
    }

    private int untilAllocating(LocalDate start,
            ResourcesPerDayModification resourcesPerDayModification,
            EffortDuration effortRemaining) {
        int day = 0;
        while (effortRemaining.compareTo(zero()) > 0) {
            LocalDate current = start.plusDays(day);
            EffortDuration taken = assignForDay(resourcesPerDayModification,
                    current, effortRemaining);
            effortRemaining = effortRemaining.minus(taken);
            day++;
        }
        return day;
    }

    private void setAssignmentsForEachAllocation() {
        for (Entry<ResourcesPerDayModification, List<DayAssignment>> entry : resultAssignments
                .entrySet()) {
            ResourceAllocation<?> allocation = entry.getKey()
                    .getBeingModified();
            ResourcesPerDay resourcesPerDay = entry.getKey()
                    .getGoal();
            List<DayAssignment> value = entry.getValue();
            setNewDataForAllocation(allocation, resourcesPerDay, value);
        }
    }

    protected abstract void setNewDataForAllocation(
            ResourceAllocation<?> allocation, ResourcesPerDay resourcesPerDay,
            List<DayAssignment> dayAssignments);

    protected abstract List<DayAssignment> createAssignmentsAtDay(
            ResourcesPerDayModification allocation, LocalDate day,
            EffortDuration limit);

    protected abstract boolean thereAreAvailableHoursFrom(LocalDate start,
            ResourcesPerDayModification resourcesPerDayModification,
            EffortDuration remainingDuration);

    protected abstract void markUnsatisfied(ResourceAllocation<?> beingModified);

    private EffortDuration assignForDay(
            ResourcesPerDayModification resourcesPerDayModification,
            LocalDate day, EffortDuration remaining) {
        List<DayAssignment> newAssignments = createAssignmentsAtDay(
                resourcesPerDayModification, day, remaining);
        resultAssignments.get(resourcesPerDayModification).addAll(
                newAssignments);
        return DayAssignment.sum(newAssignments);
    }

    private static class EffortPerAllocation {
        final EffortDuration duration;

        final ResourcesPerDayModification allocation;

        private EffortPerAllocation(EffortDuration duration,
                ResourcesPerDayModification allocation) {
            this.duration = duration;
            this.allocation = allocation;
        }

        public static List<EffortPerAllocation> wrap(
                List<ResourcesPerDayModification> allocations,
                List<EffortDuration> durations) {
            Validate.isTrue(durations.size() == allocations.size());
            int i = 0;
            List<EffortPerAllocation> result = new ArrayList<EffortPerAllocation>();
            for(i = 0; i < allocations.size(); i++){
                result.add(new EffortPerAllocation(durations.get(i),
                        allocations.get(i)));
            }
            return result;
        }
    }

    private class HoursPerAllocationCalculator {
        private List<ResourcesPerDayModification> allocations;

        private HoursPerAllocationCalculator(
                List<ResourcesPerDayModification> allocations) {
            this.allocations = new ArrayList<ResourcesPerDayModification>(
                    allocations);
        }

        public List<EffortPerAllocation> calculateEffortsPerAllocation(
                LocalDate start, EffortDuration toAssign) {
            do {
                List<EffortDuration> durations = divideEffort(toAssign);
                List<EffortPerAllocation> result = EffortPerAllocation.wrap(
                        allocations, durations);
                List<ResourcesPerDayModification> unsatisfied = getUnsatisfied(
                        start, result);
                if (unsatisfied.isEmpty()) {
                    return result;
                }
                for (ResourcesPerDayModification each : unsatisfied) {
                    markUnsatisfied(each.getBeingModified());
                }
                allocations.removeAll(unsatisfied);
            } while (!allocations.isEmpty());
            return Collections.emptyList();
        }

        private List<ResourcesPerDayModification> getUnsatisfied(
                LocalDate start, List<EffortPerAllocation> hoursPerAllocations) {
            List<ResourcesPerDayModification> cannotSatisfy = new ArrayList<ResourcesPerDayModification>();
            for (EffortPerAllocation each : hoursPerAllocations) {
                if (!thereAreAvailableHoursFrom(start, each.allocation,
                        each.duration)) {
                    cannotSatisfy.add(each.allocation);
                }
            }
            return cannotSatisfy;
        }

        private List<EffortDuration> divideEffort(EffortDuration toBeDivided) {
            ProportionalDistributor distributor = ProportionalDistributor
                    .create(createShares());
            int[] secondsDivided = distributor.distribute(toBeDivided
                    .getSeconds());
            return asDurations(secondsDivided);
        }

        private int[] createShares() {
            int[] result = new int[allocations.size()];
            for (int i = 0; i < result.length; i++) {
                result[i] = normalize(allocations.get(i).getGoal()
                        .getAmount());
            }
            return result;
        }

        private List<EffortDuration> asDurations(int[] secondsDivided) {
            List<EffortDuration> result = new ArrayList<EffortDuration>();
            for (int each : secondsDivided) {
                result.add(EffortDuration.seconds(each));
            }
            return result;
        }

        /**
         * Returns a normalized amount for {@link ProportionalDistributor}. For
         * example, for 2.03, 203 is returned.
         *
         * @param amount
         * @return
         */
        private int normalize(BigDecimal amount) {
            return amount.movePointRight(2).intValue();
        }

    }

}
