package com.mofangyouxuan.controller;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.propertyeditors.CustomDateEditor;
import org.springframework.validation.BindingResult;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.mofangyouxuan.common.ErrCodes;
import com.mofangyouxuan.common.PageCond;
import com.mofangyouxuan.common.SysParamUtil;
import com.mofangyouxuan.model.Goods;
import com.mofangyouxuan.model.GoodsSpec;
import com.mofangyouxuan.model.PartnerBasic;
import com.mofangyouxuan.model.PartnerStaff;
import com.mofangyouxuan.model.VipBasic;
import com.mofangyouxuan.service.GoodsService;
import com.mofangyouxuan.service.PartnerBasicService;
import com.mofangyouxuan.service.VipBasicService;
import com.mofangyouxuan.service.impl.AuthSecret;
import com.mofangyouxuan.utils.CommonUtil;

/**
 * 商品管理
 * 1、每个合作伙伴有商品数量限制；
 * 2、新添加的商品需要经过审核之后才可显示销售；
 * @author jeekhan
 *
 */
@RestController
@RequestMapping("/goods")
public class GoodsController {
	
	@Autowired
	private GoodsService goodsService;
	@Autowired
	private VipBasicService vipBasicService;
	@Autowired
	private PartnerBasicService  partnerBasicService;
	@Autowired
	private SysParamUtil sysParamUtil;
	@Autowired
	private AuthSecret authSecret;
	
	@InitBinder
	protected void initBinder(WebDataBinder binder) {
	    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
	    binder.registerCustomEditor(Date.class, new CustomDateEditor(dateFormat, true));
	}
	
	/**
	 * 添加商品
	 * @param goods
	 * @param jsonSpecArr 规格与库存json数组
	 * @param result
	 * @return {errcode:0,errmsg:'ok',goodsId:111}
	 */
	@RequestMapping("/{partnerId}/add")
	public Object addGoods(@PathVariable("partnerId")Integer partnerId,
			@Valid Goods goods,BindingResult result,
			@RequestParam(value="jsonSpecArr",required=true)String jsonSpecArr,
			@RequestParam(value="passwd",required=true)String passwd) {
		JSONObject jsonRet = new JSONObject();
		try {
			//信息验证结果处理
			if(result.hasErrors()){
				StringBuilder sb = new StringBuilder();
				List<ObjectError> list = result.getAllErrors();
				for(ObjectError e :list){
					sb.append(e.getDefaultMessage());
				}
				jsonRet.put("errmsg", sb.toString());
				jsonRet.put("errcode", ErrCodes.GOODS_PARAM_ERROR);
				return jsonRet.toString();
			}

			//规格库存检查
			JSONObject ret = this.checkSpec(goods,jsonSpecArr);
			if(ret != null) {
				return ret;
			}
			//图片链接检查
			ret = this.checkImgs(partnerId, goods);
			if(ret != null) {
				return ret;
			}
			//其他验证
			Integer limitCnt = goods.getLimitedNum();
			StringBuilder sb = new StringBuilder();
			if(limitCnt > 0) {
				String begin = goods.getBeginTime();
				String end = goods.getEndTime();
				if(begin == null| end == null) {
					sb.append("限购开始时间、限购结束时间：不可为空！");
				}
			}
			if(sb.length()>0) {
				jsonRet.put("errcode", ErrCodes.GOODS_PARAM_ERROR);
				jsonRet.put("errmsg", sb.toString());
				return jsonRet.toString();
			}
			//安全与权限检查
			PartnerBasic myPartner = this.partnerBasicService.getByID(partnerId);
			VipBasic vip = this.vipBasicService.get(goods.getUpdateOpr());
			jsonRet 	= this.authSecret.auth(myPartner, vip, passwd,PartnerStaff.TAG.goods);
			if(jsonRet.getIntValue("errcode") != 0) {
				return jsonRet.toJSONString();
			}
			if(!goods.getPartnerId().equals(myPartner.getPartnerId())) {
				jsonRet.put("errcode", ErrCodes.GOODS_PRIVILEGE_ERROR);
				jsonRet.put("errmsg", "您无权执行该操作！");
				return jsonRet.toString();
			}
			//数据处理保存
			goods.setSaledCnt(0);
			goods.setReviewResult(Goods.REWSTAT.forreview.getValue()); 
			goods.setReviewLog(null);
			goods.setReviewTime(null);
			Long id = this.goodsService.add(goods);
			if(id < 1) {
				jsonRet.put("errcode", ErrCodes.COMMON_DB_ERROR);
				jsonRet.put("errmsg", "数据保存至数据库失败！，错误码：" + id);
			}else {
				jsonRet.put("errcode", 0);
				jsonRet.put("goodsId", id);
				jsonRet.put("errmsg", "ok");
			}
		}catch(Exception e) {
			e.printStackTrace();
			jsonRet.put("errcode", ErrCodes.COMMON_EXCEPTION);
			jsonRet.put("errmsg", "出现异常，异常信息：" + e.getMessage());
		}
		return jsonRet.toString();
	}
	
	/**
	 * 修改商品基本信息
	 * @param goods
	 * @param result
	 * @return {errcode:0,errmsg:'ok',goodsId:111}
	 */
	@RequestMapping("/{partnerId}/update")
	public Object updateGoods(@PathVariable("partnerId")Integer partnerId,
			@Valid Goods goods,BindingResult result,
			@RequestParam(value="jsonSpecArr",required=true)String jsonSpecArr,
			@RequestParam(value="passwd",required=true)String passwd) {
		JSONObject jsonRet = new JSONObject();
		try {
			//信息验证结果处理
			if(result.hasErrors()){
				StringBuilder sb = new StringBuilder();
				List<ObjectError> list = result.getAllErrors();
				for(ObjectError e :list){
					sb.append(e.getDefaultMessage());
				}
				jsonRet.put("errmsg", sb.toString());
				jsonRet.put("errcode", ErrCodes.GOODS_PARAM_ERROR);
				return jsonRet.toString();
			}

			//规格库存检查
			JSONObject ret = this.checkSpec(goods,jsonSpecArr);
			if(ret != null) {
				return ret;
			}
			//图片链接检查
			ret = this.checkImgs(partnerId, goods);
			if(ret != null ) {
				return ret;
			}
			//其他验证
			Integer limitCnt = goods.getLimitedNum();
			StringBuilder sb = new StringBuilder();
			if(limitCnt > 0) {
				String begin = goods.getBeginTime();
				String end = goods.getEndTime();
				if(begin == null| end == null) {
					sb.append("限购开始时间、限购结束时间：不可为空！");
				}
			}
			if(sb.length()>0) {
				jsonRet.put("errcode", ErrCodes.GOODS_PARAM_ERROR);
				jsonRet.put("errmsg", sb.toString());
				return jsonRet.toString();
			}
			
			//安全与权限检查
			PartnerBasic myPartner = this.partnerBasicService.getByID(partnerId);
			VipBasic vip = this.vipBasicService.get(goods.getUpdateOpr());
			jsonRet 	= this.authSecret.auth(myPartner, vip, passwd,PartnerStaff.TAG.goods);
			if(jsonRet.getIntValue("errcode") != 0) {
				return jsonRet.toJSONString();
			}
			if(!goods.getPartnerId().equals(myPartner.getPartnerId())) {
				jsonRet.put("errcode", ErrCodes.GOODS_PRIVILEGE_ERROR);
				jsonRet.put("errmsg", "您无权执行该操作！");
				return jsonRet.toString();
			}
			Goods old = this.goodsService.get(false,goods.getGoodsId(),true);
			if(old == null || !goods.getPartnerId().equals(old.getPartnerId())) {
				jsonRet.put("errcode", ErrCodes.GOODS_PRIVILEGE_ERROR);
				jsonRet.put("errmsg", "您无权处理该商品信息！");
				return jsonRet.toString();
			}
			
			//数据处理保存
			if(goods.getGoodsId() == null) {
				jsonRet.put("errmsg", "商品ID：不可为空！");
				jsonRet.put("errcode", ErrCodes.GOODS_PARAM_ERROR);
				return jsonRet.toString();
			}
			
			//更新其他信息
			goods.setSaledCnt(old.getSaledCnt());
			goods.setReviewResult(Goods.REWSTAT.forreview.getValue()); 
			goods.setReviewLog("");
			goods.setReviewTime(null);
			int id = this.goodsService.update(old,goods);
			if(id <1 ) {
				jsonRet.put("errcode", ErrCodes.COMMON_DB_ERROR);
				jsonRet.put("errmsg", "数据保存至数据库失败！，错误码：" + id);
			}else {
				jsonRet.put("errcode", 0);
				jsonRet.put("goodsId", goods.getGoodsId());
				jsonRet.put("errmsg", "ok");
			}
		}catch(Exception e) {
			e.printStackTrace();
			jsonRet.put("errcode", ErrCodes.COMMON_EXCEPTION);
			jsonRet.put("errmsg", "出现异常，异常信息：" + e.getMessage());
		}
		return jsonRet.toString();
	}
	
	/**
	 * 判断是否使用了外链图片或不是自己的图片
	 * @param goods
	 * @return
	 */
	private JSONObject checkImgs(Integer partnerId,Goods goods) {
		StringBuilder sb = new StringBuilder();
		if(goods.getGoodsDesc() == null || goods.getGoodsDesc().trim().length()<1) {
			return null;
		}
		List<String> list = CommonUtil.getImgStr(goods.getGoodsDesc());
		if(list == null || list.size()<1) {
			return null;
		}
		for(String str:list) {
			str = str.trim();
			if(!str.startsWith("/shop/gimage/" + partnerId)) {
				sb.append("图片链接【" + str + "】使用不合规！");
			}
		}
		if(sb.length()>0) {
			JSONObject jsonRet = new JSONObject();
			jsonRet.put("errcode", ErrCodes.GOODS_PARAM_ERROR);
			jsonRet.put("errmsg", sb.toString());
			return jsonRet;
		}
		return null;
	}
	
	private JSONObject checkSpec(Goods goods,String jsonSpecArr) {
		JSONObject jsonRet = null;
		//规格检查
		List<GoodsSpec> specList = JSONArray.parseArray(jsonSpecArr, GoodsSpec.class);
		if(specList == null || specList.size()<1 || specList.size()>30) {
			jsonRet = new JSONObject();
			jsonRet.put("errcode", ErrCodes.GOODS_PARAM_ERROR);
			jsonRet.put("errmsg", "规格信息记录数量为1-30条！");
			return jsonRet;
		}
		StringBuilder sb = new StringBuilder();
		BigDecimal priceLowest = new BigDecimal(99999999.99);
		Integer stockSum = 0;
		for(GoodsSpec spec:specList) {
			if(spec == null) {
				sb.append("记录不可为空！");
			}else {
				if(spec.getName() == null || spec.getName().length()==0 || spec.getName().length()>20){
					sb.append("记录【" + JSONObject.toJSONString(spec) + "】规格名称不合规，须为1-20字符！");
				}
				if(spec.getVal() == null || spec.getVal()<1 || spec.getVal()>999999) {
					sb.append("记录【" + JSONObject.toJSONString(spec) + "】数量值不合规，须为1-999999的整数值！");
				}
				if(spec.getUnit() == null || spec.getUnit().length()<1 || spec.getUnit().length()>5) {
					sb.append("记录【" + JSONObject.toJSONString(spec) + "】单位不合规，长度须为1-5字符！");
				}
				if(spec.getPrice() == null || spec.getPrice().doubleValue()<0 || spec.getPrice().doubleValue()>99999999.99) {
					sb.append("记录【" + JSONObject.toJSONString(spec) + "】单价不合规，须为0-99999999.99的数值！");
				}else {
					if(priceLowest.compareTo(spec.getPrice()) > 0) {
						priceLowest = spec.getPrice();
					}
				}
				if(spec.getGrossWeight() == null || spec.getGrossWeight()<1 || spec.getGrossWeight()>99999999) {
					sb.append("记录【" + JSONObject.toJSONString(spec) + "】带包装重不合规，须为1-99999999的数值！");
				}
				if(spec.getStock() == null || spec.getStock()<0 || spec.getStock()>999999) {
					sb.append("记录【" + JSONObject.toJSONString(spec) + "】库存不合规，须为0-999999的整数值！");
				}else {
					stockSum += spec.getStock();
				}
			}
		}
		if(sb.length()>0) {
			jsonRet = new JSONObject();
			jsonRet.put("errcode", ErrCodes.GOODS_PARAM_ERROR);
			jsonRet.put("errmsg", "规格信息不合规：" + sb.toString());
			return jsonRet;
		}
		for(int i=0;i<specList.size();i++) {
			for(int j=i+1;j<specList.size();j++) {
				if(specList.get(i).getName().equals(specList.get(j).getName())) {
					jsonRet = new JSONObject();
					jsonRet.put("errcode", ErrCodes.GOODS_PARAM_ERROR);
					jsonRet.put("errmsg", "规格信息中不可出现同规格名称的记录！");
					return jsonRet;
				}
			}
		}
		goods.setPriceLowest(priceLowest);
		goods.setStockSum(stockSum);
		goods.setSpecDetail(specList);
		return null;
	}
	
	/**
	 * 根据ID获取商品信息
	 * @param needPartner 是否包含合作伙伴信息
	 * @param goodsId
	 * @return {"errcode":-1,"errmsg":"错误信息",goods:{...}} 
	 */
	@RequestMapping("/get/{goodsId}/{isSelf}/{needPartner}")
	public Object getByID(@PathVariable("goodsId")Long goodsId,
			@PathVariable("isSelf")String isSelf,
			@PathVariable("needPartner")String needPartner) {
		JSONObject jsonRet = new JSONObject();
		try {
			Goods goods = null;
			Boolean needPt = null;
			Boolean isSlf = null;
			if(needPartner != null && "1".equals(needPartner)) {
				needPt = true;
			}
			if(isSelf != null && "1".equals(isSelf)) {
				isSlf = true;
			}
			goods = this.goodsService.get(needPt,goodsId,isSlf);
			
			if(goods == null) {
				jsonRet.put("errcode", ErrCodes.GOODS_NO_EXISTS);
				jsonRet.put("errmsg", "系统中没有该商品信息！");
				return jsonRet.toString();
			}
			jsonRet.put("errcode", 0);
			jsonRet.put("errmsg", "ok");
			jsonRet.put("goods", goods);
			return jsonRet;
		}catch(Exception e) {
			e.printStackTrace();
			jsonRet.put("errcode", ErrCodes.COMMON_EXCEPTION);
			jsonRet.put("errmsg", "系统异常，异常信息：" + e.getMessage());
			return jsonRet.toString();
		}
	}
	
	/**
	 * 获取所有商品：不包含合作伙伴信息
	 * @param jsonSearchParams 查询条件 {isSelf,reviewResult,status,partnerId,upPartnerId,keywords,category,dispatchMode,city,postageId,currUserLocX,currUserLocY}
	 * @param jsonSortParams  排序条件 {time:"N#0/1",dist:"N#0",sale:"N"#0/1}；time 表示按更新上架时间排序，N为排序位置，0为升序，1为降序；dist表示按距离排序，仅对有同城条件使用;sale 为按销量
	 * @param pageCond 分页条件 
	 * @return {errcode:0,errmsg:"ok",pageCond:{},datas:[{}...]} 
	 */
	@RequestMapping("/getallnopartner")
	public Object getAllNoPartner(String jsonSearchParams,String jsonSortParams,String jsonPageCond) {
		return this.searchGoods(false, jsonSearchParams, jsonSortParams, jsonPageCond);
	}

	/**
	 * 获取所有商品：包含合作伙伴信息
	 * @param jsonSearchParams 查询条件 {isSelf,reviewResult,status,partnerId,upPartnerId,keywords,category,dispatchMode,city,postageId,currUserLocX,currUserLocY}
	 * @param jsonSortParams  排序条件 {time:"N#0/1",dist:"N#0",sale:"N"#0/1}；time 表示按更新上架时间排序，N为排序位置，0为升序，1为降序；dist表示按距离排序，仅对有同城条件使用;sale 为按销量
	 * @param pageCond 分页条件 
	 * @return {errcode:0,errmsg:"ok",pageCond:{},datas:[{}...]} 
	 */
	@RequestMapping("/getallwithpartner")
	public Object getAllWithPartner(String jsonSearchParams,String jsonSortParams,String jsonPageCond) {
		return this.searchGoods(true, jsonSearchParams, jsonSortParams, jsonPageCond);
	}
	
	
	/**
	 * 合作伙伴变更商品状态：1-上架、2-下架
	 * 
	 * @param goodsIds	待变更的商品ID列表
	 * @param partnerId	合作伙伴ID
	 * 
	 * @return {errcode:0,errmsg:"ok"}
	 * @throws JSONException 
	 */
	@RequestMapping("/{partnerId}/changeStatus")
	public String changeStatus(@RequestParam(value="goodsIds",required=true)String goodsIds,
			@PathVariable("partnerId")Integer partnerId,
			@RequestParam(value="currUserId",required=true)Integer currUserId,
			@RequestParam(value="passwd",required=true)String passwd,
			@RequestParam(value="newStatus",required=true)String newStatus) {
		JSONObject jsonRet = new JSONObject();
		try {
			//安全与权限检查
			VipBasic vip = this.vipBasicService.get(currUserId);
			PartnerBasic myPartner = this.partnerBasicService.getByID(partnerId);
			jsonRet 	= this.authSecret.auth(myPartner, vip, passwd,PartnerStaff.TAG.goods);
			if(jsonRet.getIntValue("errcode") != 0) {
				return jsonRet.toJSONString();
			} 
			String[] arr = goodsIds.split(",");
			Set<Long> okSet = new HashSet<Long>();
			Set<String> errSet = new HashSet<String>();
			for(String idStr:arr) {
				try {
					Long id = new Long(idStr);
					okSet.add(id);
				}catch(Exception e) {
					errSet.add(idStr);
				}
			}
			List<Goods> list = new ArrayList<Goods>();
			for(Long id:okSet) {
				Goods g = this.goodsService.get(false,id,true);
				if(g != null && g.getPartnerId().equals(partnerId)) {//权限验证
					list.add(g);
				}else {
					errSet.add(id.toString());
				}
			}

			if(!"1".equals(newStatus) && !"2".equals(newStatus)) { //正常或关闭
				jsonRet.put("errcode", ErrCodes.GOODS_PARAM_ERROR);
				jsonRet.put("errmsg", "状态取值不正确（1-上架，2-下架）！！");
				return jsonRet.toString();
			}
			if(list.size()>0) {
				this.goodsService.changeStatus(list, newStatus,currUserId);
			}
			
			if(errSet.size() > 0) {
				jsonRet.put("errcode", ErrCodes.GOODS_PARAM_ERROR);
				jsonRet.put("errmsg", "商品ID列表数据有误，具体数据：" + Arrays.toString(errSet.toArray()));
			}else {
				jsonRet.put("errcode", 0);
				jsonRet.put("errmsg", "ok");
			}
		}catch(Exception e) {
			//数据处理
			jsonRet.put("errcode", ErrCodes.COMMON_EXCEPTION);
			jsonRet.put("errmsg", "出现异常，异常信息：" + e.getMessage());
		}
		return jsonRet.toString();
	}
	
	/**
	 * 商品审核
	 * 
	 * @param goodsId		待审批商品ID
	 * @param rewPartnerId	审批者合作伙伴
	 * @param operator	审批人
	 * @param review 	审批意见
	 * @param result 	审批结果：S-通过，R-拒绝
	 * 
	 * @return {errcode:0,errmsg:"ok"}
	 * @throws JSONException
	 */
	@RequestMapping("/review")
	public String review(@RequestParam(value="goodsId",required=true)Long goodsId,
			@RequestParam(value="review",required=true)String review,
			@RequestParam(value="result",required=true)String result,
			@RequestParam(value="rewPartnerId",required=true)Integer rewPartnerId,
			@RequestParam(value="operator",required=true)Integer operator,
			@RequestParam(value="passwd",required=true)String passwd){
		JSONObject jsonRet = new JSONObject();
		try {
			if(!"S".equals(result) && !"R".equals(result)) {
				jsonRet.put("errcode", ErrCodes.PARTNER_PARAM_ERROR);
				jsonRet.put("errmsg", "审批结果不正确（S-通过，R-拒绝）！");
				return jsonRet.toString();
			}
			if(review == null || review.length()<2 || review.length()>600) {
				jsonRet.put("errcode", ErrCodes.PARTNER_PARAM_ERROR);
				jsonRet.put("errmsg", "审批意见：长度2-600字符！");
				return jsonRet.toString();
			}
			//数据检查
			Goods old = this.goodsService.get(true,goodsId,true);
			if(old == null) {
				jsonRet.put("errcode", ErrCodes.GOODS_STATUS_ERROR);
				jsonRet.put("errmsg", "该商品不存在于系统中！");
				return jsonRet.toString();
			}
			PartnerBasic rewPartner = this.partnerBasicService.getByID(rewPartnerId);
			if(rewPartner == null) {
				jsonRet.put("errcode", ErrCodes.PARTNER_STATUS_ERROR);
				jsonRet.put("errmsg", "审核者合作伙伴不存在！");
				return jsonRet.toString();
			}
			if(!rewPartnerId.equals(this.sysParamUtil.getSysPartnerId()) && !rewPartnerId.equals(old.getPartner().getUpPartnerId())) {
				jsonRet.put("errcode", ErrCodes.COMMON_PRIVILEGE_ERROR);
				jsonRet.put("errmsg", "您无权限执行该操作！");
				return jsonRet.toString();
			}
			
			//安全与权限检查
			VipBasic vip = this.vipBasicService.get(operator);
			jsonRet 	= this.authSecret.auth(rewPartner, vip, passwd, PartnerStaff.TAG.reviewgds);
			if(jsonRet.getIntValue("errcode") != 0) {
				return jsonRet.toJSONString();
			}
			
			int cnt = this.goodsService.review(old, rewPartnerId,operator, result, review);
			if(cnt < 1) {
				jsonRet.put("errcode", ErrCodes.COMMON_DB_ERROR);
				jsonRet.put("errmsg", "数据保存至数据库失败！");
			}else {
				jsonRet.put("errcode", 0);
				jsonRet.put("errmsg", "ok");
			}
		}catch(Exception e) {
			//异常处理
			jsonRet.put("errcode", ErrCodes.COMMON_EXCEPTION);
			jsonRet.put("errmsg", "出现异常，异常信息：" + e.getMessage());
		}
		return jsonRet.toString();
	}
	
	
	/**
	 * 修改商品规格与库存
	 * @param partnerId
	 * @param goodsId
	 * @param specDetail
	 * @return {errcode:0,errmsg:'ok'}
	 */
	@RequestMapping("/{partnerId}/changeSpec/{goodsId}")
	public JSONObject changeSpec(@PathVariable("partnerId")Integer partnerId,
			@RequestParam(value="currUserId",required=true)Integer currUserId,
			@RequestParam(value="passwd",required=true)String passwd,
			@PathVariable(value="goodsId",required=true)Long goodsId,
			@RequestParam(value="jsonSpecArr",required=true)String jsonSpecArr) {
		JSONObject jsonRet = new JSONObject();
		try {
			//安全与权限检查
			jsonRet 	= this.authSecret.auth(partnerId, currUserId, passwd, PartnerStaff.TAG.goods);
			if(jsonRet.getIntValue("errcode") != 0) {
				return jsonRet;
			}
			Goods goods = this.goodsService.get(false, goodsId,true);
			if(goods == null || !goods.getPartnerId().equals(partnerId)) {
				jsonRet.put("errmsg", "您没有权限执行该操作！");
				jsonRet.put("errcode", ErrCodes.PARTNER_NO_EXISTS);
				return jsonRet;
			}
			//规格库存检查
			Goods updGoods = new Goods();
			JSONObject ret = this.checkSpec(updGoods,jsonSpecArr);
			if(ret != null) {
				return ret;
			}			//数据保存
			int cnt = this.goodsService.changeSP(goodsId, JSONArray.parseArray(jsonSpecArr, GoodsSpec.class), 1,updGoods.getStockSum(), updGoods.getPriceLowest(),currUserId);
			if(cnt < 1) {
				jsonRet.put("errcode", ErrCodes.COMMON_DB_ERROR);
				jsonRet.put("errmsg", "数据保存至数据库失败！");
			}else {
				jsonRet.put("errcode", 0);
				jsonRet.put("errmsg", "ok");
			}
		}catch(Exception e) {
			//异常处理
			jsonRet.put("errcode", ErrCodes.COMMON_EXCEPTION);
			jsonRet.put("errmsg", "出现异常，异常信息：" + e.getMessage());
		}
		return jsonRet;
	}
	
	
	private Object searchGoods(boolean needPartner,String jsonSearchParams,String jsonSortParams,String jsonPageCond) {
		JSONObject jsonRet = new JSONObject();
		try {
			Map<String,Object> params = new HashMap<String,Object>();
			params.put("reviewResult", Goods.REWSTAT.normal.getValue());	//默认审核通过
			params.put("status", "1");	//默认已上架
			params.put("partnerStatus", "S"); //合作伙伴状态为审核通过
			if(jsonSearchParams != null && jsonSearchParams.length()>0) {
				JSONObject jsonSearch = JSONObject.parseObject(jsonSearchParams);
				if(jsonSearch.containsKey("isSelf") && jsonSearch.getBooleanValue("isSelf")) {//合作伙伴自己
					params.put("reviewResult", null);
					params.put("status", null);
					params.put("partnerStatus", null);
				}
				if(jsonSearch.containsKey("reviewResult")) {
					params.put("reviewResult", jsonSearch.getString("reviewResult"));
				}
				if(jsonSearch.containsKey("status")) {
					params.put("status", jsonSearch.getString("status"));
				}
				if(jsonSearch.containsKey("partnerId")) {
					params.put("partnerId", jsonSearch.getInteger("partnerId"));
				}
				if(jsonSearch.containsKey("upPartnerId")) {
					params.put("upPartnerId", jsonSearch.getInteger("upPartnerId"));
				}
				if(jsonSearch.containsKey("keywords")) {//使用关键字查询
					params.put("goodsName", jsonSearch.getString("keywords"));
				}
				if(jsonSearch.containsKey("categoryId")) {
					params.put("categoryId", jsonSearch.getInteger("categoryId"));
				}
				if(jsonSearch.containsKey("dispatchMode")) {
					params.put("dispatchMode", jsonSearch.getString("dispatchMode"));
				}
				if(jsonSearch.containsKey("city")) {//指定城市
					params.put("city", jsonSearch.getString("city"));
					if(jsonSearch.containsKey("currUserLocX") && jsonSearch.containsKey("currUserLocY")) {
						params.put("currUserLocX", jsonSearch.getBigDecimal("currUserLocX"));
						params.put("currUserLocY", jsonSearch.getBigDecimal("currUserLocY"));
					}
				}
				if(jsonSearch.containsKey("area")) {
					params.put("area", jsonSearch.getString("area"));
				}
				
				if(jsonSearch.containsKey("postageId")) {
					params.put("postageId", jsonSearch.getString("postageId"));
				}
			}
			String strSorts = null;
			if(jsonSortParams != null && jsonSortParams.length()>0) {
				JSONObject jsonSort = JSONObject.parseObject(jsonSortParams);
				Map<Integer,String> sortMap = new HashMap<Integer,String>();
				if(jsonSort.containsKey("time")) {
					String value = jsonSort.getString("time");
					if(value != null && value.length()>0) {
						String[] arr = value.split("#");
						sortMap.put(new Integer(arr[0]), ("0".equals(arr[1]))? " update_time asc " : " update_time desc " );
					}
				}
				if(jsonSort.containsKey("dist")) {
					String value = jsonSort.getString("dist");
					if(value != null && value.length()>0) {
						String[] arr = value.split("#");
						sortMap.put(new Integer(arr[0]), " distance asc " );
					}
				}
				if(jsonSort.containsKey("sale")) {
					String value = jsonSort.getString("sale");
					if(value != null && value.length()>0) {
						String[] arr = value.split("#");
						sortMap.put(new Integer(arr[0]), ("0".equals(arr[1]))? " saled_cnt asc " : " saled_cnt desc " );
					}
				}
				
				Set<Integer> set = new TreeSet<Integer>(sortMap.keySet());
				StringBuilder sb = new StringBuilder();
				for(Integer key:set) {
					sb.append(",");
					sb.append(sortMap.get(key));
				}
				if(sb.length()>0) {
					strSorts = " order by " + sb.substring(1);
				}
			}
			PageCond pageCond = null;
			if(jsonPageCond == null || jsonPageCond.length()<1) {
				pageCond = new PageCond(0,100);
			}else {
				pageCond = JSONObject.toJavaObject(JSONObject.parseObject(jsonPageCond), PageCond.class);
				if(pageCond == null) {
					pageCond = new PageCond(0,100);
				}
				if( pageCond.getBegin()<=0) {
					pageCond.setBegin(0);
				}
				if(pageCond.getPageSize()<2) {
					pageCond.setPageSize(100);
				}
			}
			int cnt = this.goodsService.countAll(params);
			pageCond.setCount(cnt);
			jsonRet.put("pageCond", pageCond);
			jsonRet.put("errcode", ErrCodes.GOODS_NO_GOODS);
			jsonRet.put("errmsg", "没有获取到商品信息！");
			if(cnt>0) {
				List<Goods> list = this.goodsService.getAll(needPartner,params, strSorts, pageCond);
				if(list != null && list.size()>0) {
					jsonRet.put("datas", list);
					jsonRet.put("errcode", 0);
					jsonRet.put("errmsg", "ok");
				}
			}
			return jsonRet.toString();
		}catch(Exception e) {
			e.printStackTrace();
			jsonRet.put("errcode", ErrCodes.COMMON_EXCEPTION);
			jsonRet.put("errmsg", "系统异常，异常信息：" + e.getMessage());
			return jsonRet.toString();
		}
	}
}
