package com.serviceimpl;

import java.sql.Blob;
import java.util.Date;
import java.util.List;

import org.hibernate.HibernateException;
import org.hibernate.LobHelper;
import org.hibernate.Query;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.transform.Transformers;

import com.google.appengine.repackaged.com.google.common.base.StringUtil;


public class HibernateUtil implements HibernateService{			
	
	private int saveid;
	
	HibernateConfigSingleton hibernateConfig = HibernateConfigSingleton.getInstance();
	SessionFactory sessionfactory = hibernateConfig.getSessionFactory();
	
	public int sqlqueryupdate(String query){
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		Query q=null;
		int flag=0;
		try{
		tx =session.beginTransaction();
	    q=session.createQuery(query);	    
	    flag=q.executeUpdate();
		tx.commit();
		}catch (HibernateException e) {
		if (tx!=null) tx.rollback();
		}finally{
		session.close(); 
		}
		return flag;
   }
					
	public int save(Object obj){
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		try{			
		tx = session.beginTransaction();
		saveid = (Integer)session.save(obj);
		tx.commit();
		}catch(HibernateException e){
			 if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
		return saveid;
	}

	
	public void persist(Object obj) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		try{
		tx = session.beginTransaction();
		session.persist(obj);
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
	}
				
	public List getList(String query) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		List list = null;
		try{
		tx = session.beginTransaction();	
		list =  session.createQuery(query).list();
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
		return list;
	}

	public List getListWithParameter(String queryStr,List<String> name,List<String> value) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		List objList = null;
		try{
		   tx = session.beginTransaction();
	       Query query=session.createQuery(queryStr);
	       for(int p=0;p<name.size();p++){
	         query.setParameter(name.get(p), value.get(p));
	       }
	       objList=query.list();
	       tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
	       return objList;
    }


	public Object load(String className, Integer id) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		Object obj = null;
		try{
		tx = session.beginTransaction();
		obj=session.load(className,id);
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
		return obj;
	}

		
	public List getSQLListEntity(String query, String classname) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		List list = null;
		try{
		tx = session.beginTransaction();
		list =  session.createSQLQuery(query).addEntity(classname).list();
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
		return list;
	}


	public int saveMultiple(Object obj) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		try{
		tx = session.beginTransaction();
		saveid = (Integer)session.save(obj);
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
		return saveid;
	}


	public int updateRet(Object obj) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		try{
		tx = session.beginTransaction();
		saveid = (Integer)session.save(obj);
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
		return saveid;
	}


	public List getListPaging(String queryStr, int firstrange, int lastrange) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		List objList = null;
		try{
		tx = session.beginTransaction();
		Query query =  session.createQuery(queryStr);
		query.setFirstResult(firstrange);
		query.setMaxResults(lastrange);  
	    objList=query.list();
	    tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
		return objList;		
	}


	public List getSQLList(String query) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		List list = null;
		try{
		tx = session.beginTransaction();
		list =  session.createSQLQuery(query).list();
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
		return list;
	}

	public List getSQLList1(String query,Class s) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		List list = null;
		try{
		tx = session.beginTransaction();
		list =  session.createSQLQuery(query)
		        .setResultTransformer(Transformers.aliasToBean(s))
		        .list();
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
		return list;
	}


	public void delete(Object obj) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		try{
		tx = session.beginTransaction();		
		session.delete(obj);
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
	}
	
	public void update(Object obj) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		try{
		tx = session.beginTransaction();
		session.saveOrUpdate(obj);
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
	}
     
	public void update1(Object obj) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		try{
		tx = session.beginTransaction();
		session.update(obj);
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
	}

	public Object get(String className, Integer id) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		Object obj = null;
		try{
		tx = session.beginTransaction();
		obj=session.get(className,id);
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
		return obj;
	}
		
	public Object getObjectWithQuery(String queryStr) {
		Session session = sessionfactory.openSession();
	    Transaction tx = null;
	    Query query=null;
	    Object objList=null;
	    try{
	    tx =session.beginTransaction();
	    query=session.createQuery(queryStr);
	    objList=query.uniqueResult();
	    tx.commit();
	    }catch (HibernateException e) {
	    if (tx!=null) tx.rollback();
	    }finally {
	    session.close(); 
	    }
	   return objList;
	 }
	
	public void saveOrUpdate(Object obj) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		try{
		tx = session.beginTransaction();
		session.saveOrUpdate(obj);
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
	}


	
	public List getListParameterDate(String queryStr, List<String> name,
			List<Date> value) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		List objList = null;
		try{
		   tx = session.beginTransaction();
	       Query query=session.createSQLQuery(queryStr);
	       for(int p=0;p<name.size();p++){
	         query.setParameter(name.get(p), value.get(p));
	       }
	       objList=query.list();
	       tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
		return objList;
	}


	public List getListWithParameterList(String queryStr, String name, List<Integer> list) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		List objList = null;
		try{
		   tx = session.beginTransaction();
	       Query query=session.createQuery(queryStr);	       
	       query.setParameterList(name, list);	       
	       objList=query.list();
	       tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
	       return objList;
	}

	
	public List<Integer> getSQLListInteger(String query) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		List<Integer> list = null;
		try{
		tx = session.beginTransaction();
		list =  session.createSQLQuery(query).list();
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
		return list;
	}

	
	public List getListWithParameterNameandList(String queryStr, String param1,	Integer name1, String param2, List<Integer> name2) {
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		List objList = null;
		try{
		   tx = session.beginTransaction();
	       Query query=session.createQuery(queryStr);	
	       query.setParameter(param1, name1);
	       query.setParameterList(param2, name2);	       
	       objList=query.list();
	       tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}
	       return objList;
	}
	
	
	public int bulkDelete(String query) {

		Session session = sessionfactory.openSession();
		Transaction tx = null;
		int flag = 0;
		try{
			tx = session.beginTransaction();
			Query q = session.createQuery(query);
			flag = q.executeUpdate();
			tx.commit();
		}catch (HibernateException e) {
			if (tx!=null) tx.rollback();
		}finally{
			session.close(); 
		}
		return flag;
		
	}

	public void updateSQLQuery(String queryStr) {
		// TODO Auto-generated method stub
		org.hibernate.Session session = hibernateConfig.getSessionFactory().openSession();
		Transaction tx = null;
		try{
		tx = session.beginTransaction();
		/*System.out.println(queryStr);*/
		Query query = session.createSQLQuery(queryStr);
		/*System.out.println("**************");*/
		int a=query.executeUpdate();
		System.out.println(a);
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}		
		
	}
	
	public List<?> getSQLQueryList(String query,String className) {
		 List listObject =null;
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		
		try{
			tx = session.beginTransaction();
			System.out.println(query);
			SQLQuery query1 = session.createSQLQuery(query);
			query1.addEntity(className);
			  listObject =query1.list();
			tx.commit();
		}catch (HibernateException e) {
			if (tx!=null) tx.rollback();
		}finally{
			session.close(); 
		}
		return listObject;
		
	}
	public int updateSQLQuery1(String queryStr) {
		// TODO Auto-generated method stub
		int a=0;
		org.hibernate.Session session = hibernateConfig.getSessionFactory().openSession();
		Transaction tx = null;
		try{
		tx = session.beginTransaction();
		/*System.out.println(queryStr);*/
		Query query = session.createSQLQuery(queryStr);
		/*System.out.println("**************");*/
		a=query.executeUpdate();
		
		tx.commit();
		}catch(HibernateException e){
			if (tx!=null) tx.rollback();
	         e.printStackTrace(); 
		}finally{
			session.close();
		}		
		
		return a;
	}
	/*SQLQuery query = session.createSQLQuery(sql);
    query.addEntity(Employee.class);
    List employees = query.list();*/

	public Object generateBolb(byte[] name){
		Session session = sessionfactory.openSession();
		Transaction tx = null;
		LobHelper lobHelper=null;
		Blob bolb=null;
		try{
		tx =session.beginTransaction();
		
		lobHelper= session.getLobHelper();
		bolb=lobHelper.createBlob(name);
		tx.commit();
		}catch (Exception e) {
		    e.printStackTrace();
		if (tx!=null) tx.rollback();
		}finally {
			 if(session!=null && session.isOpen())
				 session.close();  
		}
		return bolb;
	}

	public List getListCreateQuery(String queryStr,String tableStr) {
	    Session session = sessionfactory.openSession();
		Query query=null;
		List objList=null;
		try{
		  query=session.createSQLQuery(queryStr);
	   	  objList=query.list();
		}catch (HibernateException e) {
			 if(StringUtil.isEmptyOrWhitespace(tableStr)!=true) {
				 updateSQLQuery("DROP TABLE IF EXISTS "+tableStr+""); 
			 }	
		}finally{
			 if(session!=null && session.isOpen())
				 session.close(); 
		}
		return objList;
   }
	
	
}
