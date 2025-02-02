package com.buleqr.service.impl;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.buleqr.dao.PunchCardDao;
import com.buleqr.dao.QrCodeUserFileDao;
import com.buleqr.dao.UserDao;
import com.buleqr.dto.request.ChangeToEmailBody;
import com.buleqr.dto.request.CustomerRegisterBody;
import com.buleqr.dto.request.RetrievePasswordBody;
import com.buleqr.dto.request.UpdatePwdReq;
import com.buleqr.dto.response.HomeUserInfoVo;
import com.buleqr.enums.QrCodeState;
import com.buleqr.enums.UserState;
import com.buleqr.exception.ServiceException;
import com.buleqr.mapper.UserMapper;
import com.buleqr.pojo.*;
import com.buleqr.security.JwtUser;
import com.buleqr.service.PunchCardService;
import com.buleqr.service.QrCodeUserFileService;
import com.buleqr.service.UserService;
import com.buleqr.service.VerificationCodeManager;
import com.buleqr.utils.security.SecurityUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.Objects;

/**
 * @author buleqr
 */
@Service
public class UserServiceImpl implements UserService {
    private final UserMapper userMapper;
    private final UserDao userDao;
    private final VerificationCodeManager verificationCodeManager;
    private final QrCodeUserFileService qrCodeUserFileService;
    private final QrCodeUserFileDao qrCodeUserFileDao;
    private final PunchCardDao punchCardDao;

    public UserServiceImpl(UserMapper userMapper, UserDao userDao, VerificationCodeManager verificationCodeManager, QrCodeUserFileService qrCodeUserFileService, QrCodeUserFileDao qrCodeUserFileDao, PunchCardService punchCardService, PunchCardDao punchCardDao) {
        this.userMapper = userMapper;
        this.userDao = userDao;
        this.verificationCodeManager = verificationCodeManager;
        this.qrCodeUserFileService = qrCodeUserFileService;
        this.qrCodeUserFileDao = qrCodeUserFileDao;
        this.punchCardDao = punchCardDao;
    }

    @Override
    @Transactional
    public Boolean register(CustomerRegisterBody registerBody) {
        boolean isOk = verificationCodeManager.checkVerificationCode(registerBody.getEmail(), registerBody.getVerificationCode());
        if (!isOk) {
            throw new ServiceException("验证码错误",400);
        }
        if(userDao.lambdaQuery().eq(User::getEmail,registerBody.getEmail()).count()>0) {
            throw new ServiceException("注册失败，邮箱已存在",400);
        }
        QrCodeUserFile qrCodeUserFile = qrCodeUserFileService.bindingRegister(registerBody.getQrUuid());
        if (Objects.isNull(qrCodeUserFile)) {
            throw new ServiceException("该二维码不存在",400);
        }
        if (qrCodeUserFile.getQrCodeState().equals(QrCodeState.IS_BINDING)) {
            throw new ServiceException("抱歉，该二维码已被绑定",400);
        }
        Date date = new Date();
        User user = new User();
        user.setQrUuid(qrCodeUserFile.getQrUuid());
        user.setQrId(qrCodeUserFile.getId());
        user.setEmail(registerBody.getEmail());
        user.setUsername(RandomUtil.randomString(5));
        user.setUserType(0);
        user.setRole("consumer");
        user.setCreateTime(date);
        user.setPassword(SecurityUtils.encryptPassword(registerBody.getPassword()));
        if (!userDao.save(user)) {
            throw new ServiceException("注册失败",400);
        }
        qrCodeUserFile.setQrCodeState(QrCodeState.IS_BINDING);
        qrCodeUserFile.setLastScanTime(date);
        boolean b = qrCodeUserFileDao.updateById(qrCodeUserFile);
        if (!b) {
            throw new ServiceException("注册失败",400);
        }
        //新增打卡记录
        PunchCard punchCard = new PunchCard();
        punchCard.setCreatorId(user.getId());
        punchCard.setCreateTime(date);
        boolean save = punchCardDao.save(punchCard);
        if (!save) {
            throw new ServiceException("注册失败",400);
        }
        return true;
    }

    @Override
    public Boolean adminRegister(User user) {
        if(userDao.lambdaQuery().eq(User::getEmail,user.getEmail()).count()>0) {
            throw new ServiceException("注册失败，手机号码已存在",400);
        }
        if(userDao.lambdaQuery().eq(User::getUsername,user.getUsername()).count()>0) {
            throw new ServiceException("注册失败，用户名已存在",400);
        }
        user.setUsername(user.getEmail().substring(6,11));
        user.setUserType(1);
        user.setRole("admin");
        user.setCreateTime(new Date());
        user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        if (userMapper.insert(user)==0) {
            throw new ServiceException("注册失败",400);
        }
        return true;
    }

    @Override
    public Boolean updateUser(User user) {
        JwtUser loginUser = SecurityUtils.getLoginUser();
        if (userDao.lambdaQuery().eq(User::getId,loginUser.getId()).count()==0) {
            throw new ServiceException("该用户不存在",400);
        }
        if(userDao.lambdaQuery().eq(User::getEmail,user.getEmail()).ne(User::getId,loginUser.getId()).count()>0) {
            throw new ServiceException("修改失败，邮箱已存在",400);
        }
        if(userDao.lambdaQuery().eq(User::getUsername,user.getUsername()).ne(User::getId,loginUser.getId()).count()>0) {
            throw new ServiceException("修改失败，用户名已存在",400);
        }
        //给密码设置加密
        if (user.getPassword()!=null&& StringUtils.isNoneEmpty(user.getPassword())) {
            user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        }else {
            user.setPassword(null);
        }
        if(!userDao.updateById(user)) {
            throw new ServiceException("修改失败",400);
        }
        return true;
    }

    @Override
    public Boolean adminUpdateUser(User user) {
        JwtUser loginUser = SecurityUtils.getLoginUser();
        if (userDao.lambdaQuery().eq(User::getId,loginUser.getId()).count()==0) {
            throw new ServiceException("该用户不存在",400);
        }
        //给密码设置加密
        if (user.getPassword()!=null&& StringUtils.isNoneEmpty(user.getPassword())) {
            user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
        }else {
            user.setPassword(null);
        }
        if(!userDao.updateById(user)) {
            throw new ServiceException("修改失败",400);
        }
        return true;
    }

    @Override
    public PageList<User> ListUser(PageDomain pageDomain) {
        Page<User> page = userDao.lambdaQuery().eq(User::getUserType,0)
                .page(new Page<>(pageDomain.getPageNum(), pageDomain.getPageSize()));
        return PageList.of(page.getRecords(),page);
    }

    @Override
    public PageList<User> AdminListUser(PageDomain pageDomain) {
        Page<User> page = userDao.lambdaQuery().eq(User::getUserType,1).page(new Page<>(pageDomain.getPageNum(), pageDomain.getPageSize()));
        return PageList.of(page.getRecords(),page);
    }

    @Override
    public Boolean deletedUser(Long id) {
        JwtUser loginUser = SecurityUtils.getLoginUser();
        if(loginUser.getId()!=1)
        throw new ServiceException("无此操作权限",401);
        if (userDao.lambdaQuery().eq(User::getId,loginUser.getId()).count()==0) {
            throw new ServiceException("当前用户不存在",400);
        }
        if(!userDao.removeById(id)) {
            throw new ServiceException("删除失败",400);
        }
        return true;
    }

    @Override
    public User getUserInfo() {
        JwtUser loginUser = SecurityUtils.getLoginUser();
        return userDao.getById(loginUser.getId());
    }

    @Override
    public User getUserByName(String userName) {
        User user = userDao.lambdaQuery()
                .eq(User::getEmail, userName)
                .eq(User::getStatus, UserState.valid)
                .one();
        return user;
    }

    @Override
    public Boolean updatePwdReq(UpdatePwdReq updatePwdReq) {
        JwtUser loginUser = SecurityUtils.getLoginUser();
        User user = userDao.lambdaQuery()
                .eq(User::getId, loginUser.getId())
                .select(User::getPassword)
                .one();

        if (!SecurityUtils.matchesPassword(updatePwdReq.getPassword(),user.getPassword())) {
            throw new ServiceException("原密码不正确",400);
        }
        boolean update = userDao.lambdaUpdate()
                .set(User::getPassword, SecurityUtils.encryptPassword(updatePwdReq.getPassword2()))
                .eq(User::getId, loginUser.getId())
                .update();
        if (!update) {
            throw new ServiceException("修改密码失败",400);
        }
        return update;
    }

    @Override
    public boolean changeToEmail(ChangeToEmailBody changeToEmailBody) {
        JwtUser loginUser = SecurityUtils.getLoginUser();
        if (Objects.isNull(loginUser)) {
            throw new ServiceException("请先登录",400);
        }
        User loginuser = userDao.lambdaQuery().eq(User::getId, loginUser.getId()).one();
        if (Objects.isNull(loginuser)) {
            throw new ServiceException("该用户不存在",400);
        }
        if (loginuser.getEmail().equals(changeToEmailBody.getEmail())) {
            throw new ServiceException("新邮箱不能与旧邮箱相同",400);
        }
        boolean isOk = verificationCodeManager.checkVerificationCode(changeToEmailBody.getEmail(), changeToEmailBody.getVerificationCode());
        if (!isOk) {
            throw new ServiceException("验证码错误",400);
        }
        if(userDao.lambdaQuery().eq(User::getEmail,changeToEmailBody.getEmail()).count()>0) {
            throw new ServiceException("换绑失败，此邮箱已存在",400);
        }
        User user = new User();
        user.setEmail(changeToEmailBody.getEmail());
        user.setId(loginUser.getId());
        boolean b = userDao.updateById(user);
        if (!b) {
            throw new ServiceException("换绑失败",400);
        }
        return true;
    }

    @Override
    public boolean retrievePassword(RetrievePasswordBody body) {
        User eUser = userDao.lambdaQuery().eq(User::getEmail, body.getEmail()).one();
        if (Objects.isNull(eUser)) {
            throw new ServiceException("该邮箱还未注册",400);
        }
        boolean isOk = verificationCodeManager.checkVerificationCode(body.getEmail(), body.getVerificationCode());
        if (!isOk) {
            throw new ServiceException("验证码错误",400);
        }
        if (!body.getPassword().equals(body.getPassword1())) {
            throw new ServiceException("新密码与确认密码不一致",400);
        }
        User user = new User();
        user.setPassword(SecurityUtils.encryptPassword(body.getPassword()));
        user.setId(eUser.getId());
        boolean b = userDao.updateById(user);
        if (!b) {
            throw new ServiceException("找回密码失败",400);
        }
        return true;
    }

    @Override
    public HomeUserInfoVo getHomeUserInfoVo() {
        JwtUser loginUser = SecurityUtils.getLoginUser();
        if (Objects.isNull(loginUser)) {
            throw new ServiceException("请先登录",400);
        }
        User user = userDao.getById(loginUser.getId());
        if (Objects.isNull(user)) {
            throw new ServiceException("该用户不存在",400);
        }
        Date date = new Date();
        long betweenDay = DateUtil.between(user.getCreateTime(), date, DateUnit.DAY);
        HomeUserInfoVo homeUserInfoVo = new HomeUserInfoVo();
        homeUserInfoVo.setUsername(user.getUsername());
        homeUserInfoVo.setInNumberOfDays(betweenDay);
        Integer count = punchCardDao.lambdaQuery()
                .eq(PunchCard::getCreatorId, user.getId())
                .count();
        String designation = "青铜";
        if (count>30&&count<=50) {
            designation = "白银";
        }else if (count>50&&count<=80) {
            designation = "黄金";
        }else if (count>80&&count<=150) {
            designation = "铂金";
        }else if (count>150&&count<=300) {
            designation = "钻石";
        }else if (count>300) {
            designation = "王者";
        }
        homeUserInfoVo.setDesignation(designation);
        return homeUserInfoVo;
    }
}
