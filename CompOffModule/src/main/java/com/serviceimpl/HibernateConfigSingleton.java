package com.serviceimpl;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;


public class HibernateConfigSingleton {

	private static final HibernateConfigSingleton INSTANCE = new HibernateConfigSingleton();

	private final SessionFactory sessionFactory;

	private HibernateConfigSingleton() {
		try {
			Configuration cfg = new Configuration().configure();
			override(cfg, "hibernate.connection.url", "DB_URL");
			override(cfg, "hibernate.connection.username", "DB_USER");
			override(cfg, "hibernate.connection.password", "DB_PASSWORD");
			// Existing tables (e.g. Staffs.dateofJoining / createdDate) can hold zero dates '0000-00-00': read them as
			// NULL instead of failing the whole query. Passed to the MySQL driver; hibernate.cfg.xml may set its own value.
			if (cfg.getProperty("hibernate.connection.zeroDateTimeBehavior") == null) {
				cfg.setProperty("hibernate.connection.zeroDateTimeBehavior", "CONVERT_TO_NULL");
			}
			sessionFactory = cfg.buildSessionFactory();
		} catch (Throwable ex) {
			throw new ExceptionInInitializerError(ex);
		}
	}
	
	

	private static void override(Configuration cfg, String property, String name) {
		String value = System.getProperty(name);
		if (value == null) {
			value = System.getenv(name);
		}
		if (value != null) {
			cfg.setProperty(property, value);
		}
	}

	public static HibernateConfigSingleton getInstance() {
		return INSTANCE;
	}

	public SessionFactory getSessionFactory() {
		return sessionFactory;
	}
}
