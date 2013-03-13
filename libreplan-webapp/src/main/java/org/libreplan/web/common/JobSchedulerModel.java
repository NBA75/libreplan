/*
 * This file is part of LibrePlan
 *
 * Copyright (C) 2013 Igalia, S.L.
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

package org.libreplan.web.common;

import java.util.List;

import org.libreplan.business.common.daos.IConnectorDAO;
import org.libreplan.business.common.daos.IJobSchedulerConfigurationDAO;
import org.libreplan.business.common.entities.Connector;
import org.libreplan.business.common.entities.ConnectorException;
import org.libreplan.business.common.entities.JobClassNameEnum;
import org.libreplan.business.common.entities.JobSchedulerConfiguration;
import org.libreplan.business.common.exceptions.InstanceNotFoundException;
import org.libreplan.business.common.exceptions.ValidationException;
import org.libreplan.importers.IExportTimesheetsToTim;
import org.libreplan.importers.IImportRosterFromTim;
import org.libreplan.importers.ISchedulerManager;
import org.libreplan.importers.TimImpExpInfo;
import org.libreplan.web.common.concurrentdetection.OnConcurrentModification;
import org.quartz.SchedulerException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Model for UI operations related to {@link JobSchedulerConfiguration}.
 *
 * @author Manuel Rego Casasnovas <rego@igalia.com>
 * @author Miciele Ghiorghis <m.ghiorghis@antoniusziekenhuis.nl>
 */
@Service
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
@OnConcurrentModification(goToPage = "/common/job_scheduling.zul")
public class JobSchedulerModel implements IJobSchedulerModel {

    private JobSchedulerConfiguration jobSchedulerConfiguration;

    @Autowired
    private ISchedulerManager schedulerManager;

    @Autowired
    private IJobSchedulerConfigurationDAO jobSchedulerConfigurationDAO;

    @Autowired
    private IConnectorDAO connectorDAO;

    @Autowired
    private IImportRosterFromTim importRosterFromTim;

    @Autowired
    private IExportTimesheetsToTim exportTimesheetsToTim;

    private TimImpExpInfo timImpExpInfo;

    @Override
    @Transactional(readOnly = true)
    public List<JobSchedulerConfiguration> getJobSchedulerConfigurations() {
        return jobSchedulerConfigurationDAO.getAll();
    }

    @Override
    public String getNextFireTime(
            JobSchedulerConfiguration jobSchedulerConfiguration) {
        return schedulerManager.getNextFireTime(jobSchedulerConfiguration);
    }

    @Override
    public void doManual(JobSchedulerConfiguration jobSchedulerConfiguration)
            throws ConnectorException {
        String name = jobSchedulerConfiguration.getJobClassName().getName();
        if (name.equals(JobClassNameEnum.IMPORT_ROSTER_FROM_TIM_JOB.getName())) {
            importRosterFromTim.importRosters();
            timImpExpInfo = importRosterFromTim.getImportProcessInfo();
            return;
        }
        if (name.equals(JobClassNameEnum.EXPORT_TIMESHEET_TO_TIM_JOB.getName())) {
            exportTimesheetsToTim.exportTimesheets();
            timImpExpInfo = exportTimesheetsToTim.getExportProcessInfo();
            return;
        }
    }

    @Override
    public TimImpExpInfo getImportExportInfo() {
        return timImpExpInfo;
    }

    @Override
    public void initCreate() {
        this.jobSchedulerConfiguration = JobSchedulerConfiguration.create();
    }

    @Override
    public void initEdit(JobSchedulerConfiguration jobSchedulerConfiguration) {
        this.jobSchedulerConfiguration = jobSchedulerConfiguration;
    }

    @Override
    public JobSchedulerConfiguration getJobSchedulerConfiguration() {
        return this.jobSchedulerConfiguration;
    }

    @Override
    @Transactional
    public void confirmSave() throws ValidationException {
        jobSchedulerConfigurationDAO.save(jobSchedulerConfiguration);
    }

    @Override
    public void cancel() {
        jobSchedulerConfiguration = null;
    }

    @Override
    @Transactional
    public void remove(JobSchedulerConfiguration jobSchedulerConfiguration) {
        try {
            jobSchedulerConfigurationDAO.remove(jobSchedulerConfiguration
                    .getId());
        } catch (InstanceNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Connector> getConnectors() {
        return connectorDAO.getAll();
    }

    @Override
    public boolean scheduleOrUnscheduleJobs(Connector connector) {
        List<JobSchedulerConfiguration> jobSchedulerConfigurations = jobSchedulerConfigurationDAO
                .findByConnectorName(connector.getName());

        for (JobSchedulerConfiguration jobSchedulerConfiguration : jobSchedulerConfigurations) {
            try {
                schedulerManager.scheduleOrUnscheduleJob(jobSchedulerConfiguration);
            } catch (SchedulerException e) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean scheduleOrUnscheduleJob() {
        try {
            schedulerManager.scheduleOrUnscheduleJob(jobSchedulerConfiguration);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to schedule job", e);
        }
        return true;
    }

    @Override
    public boolean deleteScheduledJob(
            JobSchedulerConfiguration jobSchedulerConfiguration) {
        try {
            schedulerManager.deleteJob(jobSchedulerConfiguration);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to delete job", e);
        }
        return true;
    }

}
