/*
 * This file is part of LibrePlan
 *
 * Copyright (C) 2013 St. Antoniusziekenhuis
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

package org.libreplan.web.orders;

import static org.libreplan.web.I18nHelper._;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.LogFactory;
import org.libreplan.business.common.daos.IConnectorDAO;
import org.libreplan.business.common.entities.Connector;
import org.libreplan.business.common.entities.ConnectorException;
import org.libreplan.business.common.entities.PredefinedConnectors;
import org.libreplan.business.orders.entities.OrderSyncInfo;
import org.libreplan.importers.IExportTimesheetsToTim;
import org.libreplan.importers.TimImpExpInfo;
import org.libreplan.web.common.IMessagesForUser;
import org.libreplan.web.common.Level;
import org.libreplan.web.common.MessagesForUser;
import org.libreplan.web.common.Util;
import org.springframework.beans.factory.annotation.Autowired;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.SuspendNotAllowedException;
import org.zkoss.zk.ui.util.GenericForwardComposer;
import org.zkoss.zul.Label;
import org.zkoss.zul.SimpleListModel;
import org.zkoss.zul.Textbox;
import org.zkoss.zul.api.Window;

/**
 * Controller for Tim synchronization
 *
 * @author Miciele Ghiorghis <m.ghiorghis@antoniusziekenhuis.nl>
 */
public class TimSynchronizationController extends GenericForwardComposer {

    private static final org.apache.commons.logging.Log LOG = LogFactory
            .getLog(TimSynchronizationController.class);

    private OrderCRUDController orderController;

    private Textbox txtProductCode;
    private Label labelProductCode, labelLastSyncDate;

    @Autowired
    private IExportTimesheetsToTim exportTimesheetsToTim;

    @Autowired
    private IConnectorDAO connectorDAO;

    private Component messagesContainer;

    private IMessagesForUser messagesForUser;

    @Override
    public void doAfterCompose(Component comp) throws Exception {
        super.doAfterCompose(comp);
        comp.setVariable("timSynchronizationController", this, true);
        txtProductCode = (Textbox) comp.getFellowIfAny("txtProductCode");
        labelLastSyncDate = (Label) comp.getFellowIfAny("labelLastSyncDate");
        labelProductCode = (Label) comp.getFellowIfAny("labelProductCode");
        messagesForUser = new MessagesForUser(messagesContainer);
        updateOrderLastSyncInfoScreen();
    }

    public void startExportToTim() {
        LOG.info("startExportToTim(): " + orderController.getOrder().getName());
        txtProductCode.setConstraint("no empty:" + _("cannot be empty"));
        try {
            exportTimesheetsToTim.exportTimesheets(txtProductCode.getValue(),
                    orderController.getOrder());

            updateOrderLastSyncInfoScreen();

            shwoImpExpInfo();

        } catch (ConnectorException e) {
            messagesForUser.showMessage(Level.ERROR,
                            _("Exporting timesheets to Tim failed. Check the Tim connector"));
        }
    }

    private void updateOrderLastSyncInfoScreen() {
        OrderSyncInfo orderSyncInfo = exportTimesheetsToTim
                .getOrderLastSyncInfo(orderController.getOrder());
        if (orderSyncInfo != null) {
            if (labelLastSyncDate != null) {
                labelLastSyncDate.setValue(Util.formatDateTime(orderSyncInfo
                        .getLastSyncDate()));
            }
            if (labelProductCode != null) {
                labelProductCode.setValue("(" + orderSyncInfo.getKey() + ")");
            }
        }
    }

    public boolean isTimActivated() {
        Connector connector = connectorDAO
                .findUniqueByName(PredefinedConnectors.TIM.getName());
        if (connector == null) {
            return false;
        }
        return connector.isActivated();
    }

    private void shwoImpExpInfo() {
        Map<String, Object> args = new HashMap<String, Object>();

        TimImpExpInfo timImpExpInfo = exportTimesheetsToTim.getExportProcessInfo();
        args.put("action", _(timImpExpInfo.getAction()));
        args.put("showSuccess", timImpExpInfo.isSuccessful());
        args.put("failedReasons",
                new SimpleListModel(timImpExpInfo.getFailedReasons()));

        Window timImpExpInfoWindow = (Window) Executions.createComponents(
                "/orders/_timImpExpInfo.zul", null, args);

        try {
            timImpExpInfoWindow.doModal();
        } catch (SuspendNotAllowedException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
