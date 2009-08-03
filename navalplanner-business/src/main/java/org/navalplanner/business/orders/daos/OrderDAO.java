package org.navalplanner.business.orders.daos;

import org.navalplanner.business.common.daos.GenericDAOHibernate;
import org.navalplanner.business.orders.entities.Order;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Repository;

/**
 * Dao for {@link Order}
 * @author Óscar González Fernández <ogonzalez@igalia.com>
 */
@Repository
@Scope(BeanDefinition.SCOPE_SINGLETON)
public class OrderDAO extends GenericDAOHibernate<Order, Long> implements
        IOrderDAO {
}