package com.mofangyouxuan.service.impl;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mofangyouxuan.common.ErrCodes;
import com.mofangyouxuan.mapper.PartnerBasicMapper;
import com.mofangyouxuan.model.PartnerBasic;

@Service
public class PartnerBasicServiceImpl {
	
	@Autowired
	private PartnerBasicMapper partnerBasicMapper;
	
	/**
	 * 根据ID获取合作胡伙伴基本信息
	 * @param id	
	 * @return
	 */
	@Autowired
	public PartnerBasic getByID(Integer id) {
		return this.partnerBasicMapper.selectByPrimaryKey(id);
	}
	
	/**
	 * 根据绑定用户获取合作伙伴
	 * @param userId
	 * @return
	 */
	@Autowired
	public PartnerBasic getByBindUser(Integer userId) {
		return this.partnerBasicMapper.selectByBindUser(userId);
	}
	
	
	/**
	 * 添加合作伙伴
	 * @param basic
	 * @return 新ID或小于0的错误码
	 */
	@Autowired
	public Integer add(PartnerBasic basic) {
		basic.setId(null);
		basic.setUpdateTime(new Date());
		int cnt = this.partnerBasicMapper.insert(basic);
		if(cnt>0) {
			return basic.getId();
		}
		return ErrCodes.COMMON_DB_ERROR;
	}
	
	/**
	 * 自己更新合作伙伴信息
	 * @param basic
	 * @return 更新记录数
	 */
	@Autowired
	public int update(PartnerBasic basic) {
		basic.setUpdateTime(new Date());
		return this.partnerBasicMapper.updateByPrimaryKey(basic);
	}
	
	/**
	 * 记录审批人员的审批结果
	 * @param partnerId	合作伙伴ID
	 * @param oprId	审批人员ID
	 * @param review	审批意见
	 * @param result	审批结果
	 * @return 更新记录数
	 */
	@Autowired
	public int review(Integer partnerId,Integer oprId,String review,String result) {
		PartnerBasic basic = new PartnerBasic();
		basic.setId(partnerId);
		basic.setReviewOpr(oprId);
		basic.setReviewLog(review);
		basic.setReviewTime(new Date());
		basic.setStatus(result);
		return this.partnerBasicMapper.updateByPrimaryKey(basic);
	}
	

	/**
	 * 自己暂时关闭店铺或打开
	 * @param partnerId	合作伙伴ID
	 * @param newStatus 新的状态：S-正常，C-关闭
	 * @return 更新记录数
	 */
	@Autowired
	public int changeShopStatus(Integer partnerId,String newStatus) {
		PartnerBasic basic = new PartnerBasic();
		basic.setId(partnerId);
		basic.setStatus(newStatus);
		return this.partnerBasicMapper.updateByPrimaryKey(basic);
	}

}
