package com.serviceimpl;

import java.util.Date;
import java.util.List;

public interface HibernateService {

	public int saveMultiple(Object obj);
	public int save(Object obj);
	public int updateRet(Object obj);
	public void update(Object obj);
	public void persist(Object obj);
	public void delete(Object obj);
	public void saveOrUpdate(Object obj);
	public List getList(String query);
	public List getSQLList(String query);
	public List getSQLList1(String query,Class classname);
	public List<Integer> getSQLListInteger(String query);
	public List getSQLListEntity(String query, String classname);
	public List getListWithParameter(String queryStr,List<String> name,List<String> value);
	public List getListWithParameterList(String queryStr, String param, List<Integer> name);
	public List getListWithParameterNameandList(String queryStr, String param1, Integer name1, String param2, List<Integer> name2);
	public Object load(String className,Integer id);
	public Object get(String className,Integer id);
	public List getListPaging(String query, int firstrange, int lastrange);
	public Object getObjectWithQuery(String query);
	public List getListParameterDate(String queryStr, List<String> name, List<Date> value);	
	public int sqlqueryupdate(String query);
	public int updateSQLQuery1(String query);
	public Object generateBolb(byte[] bytename);
    public void update1(Object obj);
    public List getListCreateQuery(String queryStr,String table);//
}
